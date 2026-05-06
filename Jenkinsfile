pipeline {
    agent any

    parameters {
        booleanParam(
            name: 'DRY_RUN',
            defaultValue: false,
            description: 'When true, only run the optimizer analysis without building, testing, or deploying.'
        )
    }

    environment {
        DOCKER_HUB_CREDENTIALS = credentials('dockerhub-dumindu-credentials')
        DOCKER_IMAGE = 'beliver247/green-release-app'
        DOCKER_TAG = "${BUILD_NUMBER}"

        REMOTE_HOST = '147.15.144.192'
        REMOTE_PORT = '2510'
        REMOTE_USER = 'dumindu'
        SSH_CREDENTIALS = 'ubuntu-pc-ssh-dumindu'

        METRICS_URL = 'http://192.168.9.127:5001'
        DASHBOARD_URL = 'http://192.168.9.127:5002'
    }

    stages {
        stage('Notify Start') {
            steps {
                script {
                    env.PIPELINE_START = System.currentTimeMillis().toString()
                    env.COMMIT_SHA = ''
                    env.COMMIT_MSG = ''
                }
                sh """
                    curl -s -X POST ${METRICS_URL}/deployment/start \
                        -H "Content-Type: application/json" \
                        -d '{"job_name":"${JOB_NAME}","build_number":"${BUILD_NUMBER}"}'
                """
            }
        }

        stage('Checkout') {
            steps {
                deleteDir()
                checkout([$class: 'GitSCM',
                    branches: [[name: '*/main']],
                    userRemoteConfigs: [[
                        url: 'https://github.com/Beliver-247/green-release-demo.git',
                        credentialsId: 'github-dumindu-credentials'
                    ]],
                    extensions: [[ $class: 'CloneOption', shallow: false, depth: 0, noTags: false ]]
                ])
                script {
                    env.COMMIT_SHA = sh(script: 'git rev-parse HEAD', returnStdout: true).trim()
                    env.COMMIT_MSG = sh(script: 'git log -1 --pretty=%s', returnStdout: true).trim()
                }
            }
        }

        stage('Build Optimizer - Analyze') {
            steps {
                script {
                    def analyzeStart = System.currentTimeMillis()
                    sh 'docker pull beliver247/build-optimizer-agent:latest'
                    def output = sh(
                        script: '''
                            EXIT_CODE=0
                            tar -C "$PWD" -cf - . | docker run --rm -i --platform linux/amd64 \
                              -v /var/run/docker.sock:/var/run/docker.sock \
                              beliver247/build-optimizer-agent:latest \
                              bash -lc '
                                set -e
                                mkdir -p /work
                                tar -xf - -C /work
                                cd /work

                                git config --global --add safe.directory /work

                                HEAD_SHA=$(git rev-parse --verify HEAD)
                                if git rev-parse --verify HEAD~1 >/dev/null 2>&1; then
                                  BASE_SHA=$(git rev-parse --verify HEAD~1)
                                else
                                  BASE_SHA=4b825dc642cb6eb9a060e54bf8d69288fbee4904
                                fi

                                python3 -m optimizer \
                                  --base "$BASE_SHA" \
                                  --head "$HEAD_SHA" \
                                  --project-root /work \
                                  --dry-run true \
                                  --output-format json
                              ' || EXIT_CODE=$?

                            if [ "$EXIT_CODE" -eq 1 ]; then
                              echo "OPTIMIZER_ERROR"
                              exit 1
                            fi
                        ''',
                        returnStdout: true
                    ).trim()

                    env.OPTIMIZER_DURATION = ((System.currentTimeMillis() - analyzeStart) / 1000.0).toString()

                    echo "=== Build Optimizer Output ==="
                    echo output
                    echo "=============================="

                    // Extract the JSON line from structured output
                    def jsonLine = output.readLines().find { it.startsWith('{"') }
                    if (jsonLine) {
                        def result = new groovy.json.JsonSlurper().parseText(jsonLine)
                        env.OPTIMIZER_STATUS = result.status ?: 'unknown'

                        // Collect the build commands (skip test commands for the build stage)
                        def buildCommands = []
                        def testCommands = []
                        for (action in result.actions) {
                            if (action.name == 'build') {
                                buildCommands.add(action.command.join(' '))
                            } else if (action.name == 'test') {
                                testCommands.add(action.command.join(' '))
                            }
                        }
                        env.MAVEN_BUILD_COMMANDS = buildCommands.join('|||')
                        env.MAVEN_TEST_COMMANDS = testCommands.join('|||')

                        def affectedModules = result.affected_modules ?: []
                        env.AFFECTED_MODULES = affectedModules.join(',')

                        echo "Optimizer status: ${env.OPTIMIZER_STATUS}"
                        echo "Affected modules: ${env.AFFECTED_MODULES}"
                        echo "Build commands: ${env.MAVEN_BUILD_COMMANDS}"
                        echo "Test commands: ${env.MAVEN_TEST_COMMANDS}"
                    } else {
                        env.OPTIMIZER_STATUS = 'no_changes'
                        env.MAVEN_BUILD_COMMANDS = ''
                        env.MAVEN_TEST_COMMANDS = ''
                        env.AFFECTED_MODULES = ''
                    }

                    if (params.DRY_RUN) {
                        echo "=== DRY RUN MODE — Skipping build, test, Docker, and deploy stages ==="
                    }
                }
            }
        }

        stage('Selective Build') {
            when {
                expression { !params.DRY_RUN && env.OPTIMIZER_STATUS == 'success' && env.MAVEN_BUILD_COMMANDS?.trim() }
            }
            steps {
                script {
                    def buildStart = System.currentTimeMillis()
                    echo "Running selective Maven build for modules: ${env.AFFECTED_MODULES}"
                    env.MAVEN_BUILD_COMMANDS.split('\\|\\|\\|').each { cmd ->
                        echo "Executing: ${cmd}"
                        sh cmd
                    }
                    env.BUILD_DURATION = ((System.currentTimeMillis() - buildStart) / 1000.0).toString()
                }
            }
        }

        stage('Selective Test') {
            when {
                expression { !params.DRY_RUN && env.OPTIMIZER_STATUS == 'success' && env.MAVEN_TEST_COMMANDS?.trim() }
            }
            steps {
                script {
                    def testStart = System.currentTimeMillis()
                    echo "Running selective tests for modules: ${env.AFFECTED_MODULES}"

                    // Capture test output to count tests
                    def testOutput = ''
                    env.MAVEN_TEST_COMMANDS.split('\\|\\|\\|').each { cmd ->
                        echo "Executing: ${cmd}"
                        testOutput += sh(script: cmd, returnStdout: true)
                    }
                    env.TEST_DURATION = ((System.currentTimeMillis() - testStart) / 1000.0).toString()

                    // Parse test counts from Maven surefire output
                    def testsRun = 0
                    def matcher = (testOutput =~ /Tests run: (\d+),/)
                    while (matcher.find()) {
                        testsRun += Integer.parseInt(matcher.group(1))
                    }
                    env.TESTS_EXECUTED = testsRun.toString()
                    echo "Total tests executed: ${env.TESTS_EXECUTED}"
                }
            }
        }

        stage('Docker Build') {
            when {
                expression { !params.DRY_RUN && env.OPTIMIZER_STATUS == 'success' }
            }
            steps {
                script {
                    def dockerStart = System.currentTimeMillis()
                    dir('app') {
                        sh "docker build -t ${DOCKER_IMAGE}:${DOCKER_TAG} -t ${DOCKER_IMAGE}:latest ."
                    }
                    env.DOCKER_BUILD_DURATION = ((System.currentTimeMillis() - dockerStart) / 1000.0).toString()
                }
            }
        }

        stage('Docker Push') {
            when {
                expression { !params.DRY_RUN && env.OPTIMIZER_STATUS == 'success' }
            }
            steps {
                sh """
                    echo "${DOCKER_HUB_CREDENTIALS_PSW}" | docker login -u "${DOCKER_HUB_CREDENTIALS_USR}" --password-stdin
                    docker push ${DOCKER_IMAGE}:${DOCKER_TAG}
                    docker push ${DOCKER_IMAGE}:latest
                    docker logout
                """
            }
        }

        stage('Deploy to Server') {
            when {
                expression { !params.DRY_RUN && env.OPTIMIZER_STATUS == 'success' }
            }
            steps {
                script {
                    def deployStart = System.currentTimeMillis()
                    sshagent(credentials: ["${SSH_CREDENTIALS}"]) {
                        sh """
                            ssh -o StrictHostKeyChecking=no -p ${REMOTE_PORT} ${REMOTE_USER}@${REMOTE_HOST} \
                                'mkdir -p /home/${REMOTE_USER}/green-release-demo'
                        """
                        sh """
                            scp -o StrictHostKeyChecking=no -P ${REMOTE_PORT} docker-compose.yml \
                                ${REMOTE_USER}@${REMOTE_HOST}:/home/${REMOTE_USER}/green-release-demo/docker-compose.yml
                        """
                        sh """
                            ssh -o StrictHostKeyChecking=no -p ${REMOTE_PORT} ${REMOTE_USER}@${REMOTE_HOST} \
                                'set -e; cd /home/${REMOTE_USER}/green-release-demo; docker pull ${DOCKER_IMAGE}:latest; docker-compose down || true; docker-compose up -d; sleep 15; docker-compose ps'
                        """
                    }
                    env.DEPLOY_DURATION = ((System.currentTimeMillis() - deployStart) / 1000.0).toString()
                }
            }
        }

        stage('Smoke Test') {
            when {
                expression { !params.DRY_RUN && env.OPTIMIZER_STATUS == 'success' }
            }
            steps {
                sshagent(credentials: ["${SSH_CREDENTIALS}"]) {
                    sh """
                        ssh -o StrictHostKeyChecking=no -p ${REMOTE_PORT} ${REMOTE_USER}@${REMOTE_HOST} \
                            'curl -sf http://localhost:8081/health && echo SMOKE_TEST_PASSED || exit 1'
                    """
                }
            }
        }
    }

    post {
        always {
            script {
                // Calculate total pipeline duration matching Jenkins exactly
                def totalDuration = (System.currentTimeMillis() - currentBuild.startTimeInMillis) / 1000.0

                // Send metrics to GreenDevOps Dashboard
                def cleanCommitMsg = (env.COMMIT_MSG ?: '').replaceAll('"', '\\\\"')
                def jsonPayload = """{
                    "job_name": "${env.JOB_NAME}",
                    "build_number": "${env.BUILD_NUMBER}",
                    "pipeline_type": "optimized",
                    "commit_sha": "${env.COMMIT_SHA ?: ''}",
                    "commit_message": "${cleanCommitMsg}",
                    "status": "${currentBuild.currentResult ?: 'UNKNOWN'}",
                    "total_duration_s": ${totalDuration},
                    "build_duration_s": ${env.BUILD_DURATION ?: 'null'},
                    "test_duration_s": ${env.TEST_DURATION ?: 'null'},
                    "docker_duration_s": ${env.DOCKER_BUILD_DURATION ?: 'null'},
                    "deploy_duration_s": ${env.DEPLOY_DURATION ?: 'null'},
                    "optimizer_duration_s": ${env.OPTIMIZER_DURATION ?: 'null'},
                    "modules_built": "${env.AFFECTED_MODULES ?: ''}",
                    "modules_tested": "${env.AFFECTED_MODULES ?: ''}",
                    "tests_executed": ${env.TESTS_EXECUTED ?: 0},
                    "tests_skipped": 0,
                    "affected_modules": "${env.AFFECTED_MODULES ?: ''}",
                    "build_command": "${env.MAVEN_BUILD_COMMANDS ?: ''}",
                    "test_command": "${env.MAVEN_TEST_COMMANDS ?: ''}"
                }"""

                sh """
                    curl -s -X POST ${DASHBOARD_URL}/api/builds \
                        -H "Content-Type: application/json" \
                        -d '${jsonPayload}' || true
                """
            }

            sh "docker rmi ${DOCKER_IMAGE}:${DOCKER_TAG} || true"
            sh "docker image prune -f || true"
        }
        success {
            script {
                if (params.DRY_RUN) {
                    echo "DRY RUN COMPLETE — Build #${BUILD_NUMBER} — Analysis only, no changes deployed"
                } else if (env.OPTIMIZER_STATUS == 'success') {
                    echo "Deployment SUCCESSFUL — Build #${BUILD_NUMBER} (modules: ${env.AFFECTED_MODULES})"
                } else {
                    echo "Pipeline COMPLETE — Build #${BUILD_NUMBER} — No code changes to deploy (status: ${env.OPTIMIZER_STATUS})"
                }
            }
        }
        failure {
            echo "Deployment FAILED — Build #${BUILD_NUMBER}"
        }
    }
}
