def greenReleaseModules() {
    return ['core', 'service', 'api', 'app']
}

def discoverModuleTestInventory() {
    def inventory = [:]
    greenReleaseModules().each { moduleName ->
        def countText = sh(
            script: '''
                module="''' + moduleName + '''"
                if [ -d "$module/src/test/java" ]; then
                  grep -Rho '@Test' "$module/src/test/java" --include='*.java' 2>/dev/null | wc -l | tr -d ' '
                else
                  echo 0
                fi
            ''',
            returnStdout: true
        ).trim()
        inventory[moduleName] = countText ? countText.toInteger() : 0
    }
    return inventory
}

def readSurefireTestCounts() {
    def counts = [:]
    greenReleaseModules().each { moduleName ->
        def countText = sh(
            script: '''
                module="''' + moduleName + '''"
                report_dir="$module/target/surefire-reports"
                if [ -d "$report_dir" ] && find "$report_dir" -name 'TEST-*.xml' -type f | grep -q .; then
                  find "$report_dir" -name 'TEST-*.xml' -type f -exec awk 'BEGIN { total = 0 } /<testsuite / { if (match($0, /tests="[0-9]+"/)) { value = substr($0, RSTART + 7, RLENGTH - 8); total += value } } END { print total }' {} +
                else
                  echo 0
                fi
            ''',
            returnStdout: true
        ).trim()
        counts[moduleName] = countText ? countText.toInteger() : 0
    }
    return counts
}

def affectedModuleSet() {
    if (env.AFFECTED_MODULES == 'all') {
        return greenReleaseModules() as Set
    }
    if (!env.AFFECTED_MODULES?.trim()) {
        return [] as Set
    }
    return env.AFFECTED_MODULES.split(',').collect { it.trim() }.findAll { it } as Set
}

pipeline {
    agent any

    parameters {
        booleanParam(
            name: 'DRY_RUN',
            defaultValue: false,
            description: 'When true, only run the optimizer analysis without building or testing.'
        )
        booleanParam(
            name: 'FORCE_FULL_BUILD',
            defaultValue: false,
            description: 'Skip the optimizer analysis and force a full build and test of all modules.'
        )
        booleanParam(
            name: 'ENABLE_GREEN_SCHEDULING',
            defaultValue: true,
            description: 'Allow the pipeline to delay the build until a greener time window.'
        )
        string(
            name: 'OVERRIDE_SCHEDULE_HOUR',
            defaultValue: 'auto',
            description: 'Override ML recommendation (e.g., "5" for 5 AM). Use "auto" to let the ML model decide.'
        )
    }

    environment {
        DASHBOARD_URL            = 'http://172.17.0.1:5003'

        // Electricity Maps API Key placeholder (paste your token below)
        ELECTRICITY_MAPS_API_KEY = 'em_nGgVAPUefFX2qe8BkqzFgw3n8uGpJE2J'
    }

    stages {
        stage('Notify Start') {
            steps {
                script {
                    env.PIPELINE_START = System.currentTimeMillis().toString()
                    env.COMMIT_SHA = ''
                    env.COMMIT_MSG = ''
                }
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

                    // ── Power monitoring: start background sampler ──────────
                    // power_log.csv is written by scripts/power_monitor.sh
                    // PID is saved to power_monitor.pid so we can stop it later
                    sh '''
                        chmod +x scripts/power_monitor.sh 2>/dev/null || true
                        scripts/power_monitor.sh start power_log.csv power_monitor.pid
                    '''
                }
            }
        }

        stage('Build Optimizer - Analyze') {
            steps {
                script {
                    if (params.FORCE_FULL_BUILD) {
                        echo "=== FORCE_FULL_BUILD is enabled. Skipping Optimizer analysis ==="
                        env.OPTIMIZER_STATUS = 'success'
                        env.AFFECTED_MODULES = 'all'
                        env.MAVEN_BUILD_COMMANDS = 'mvn clean install -DskipTests'
                        env.MAVEN_TEST_COMMANDS = 'mvn test'
                        env.OPTIMIZER_DURATION = '0'
                        return
                    }

                    def analyzeStart = System.currentTimeMillis()
                    def output = sh(
                        script: '''
                            EXIT_CODE=0
                            tar -C "$PWD" -cf - . | docker run --rm -i --platform linux/amd64 \
                              -e ELECTRICITY_MAPS_API_KEY="$ELECTRICITY_MAPS_API_KEY" \
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
                                  --output-format json \
                                  --carbon-aware
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

                        // Extract scheduling metrics if present
                        if (result.scheduling) {
                            env.CARBON_INTENSITY = result.scheduling.current_intensity?.toString() ?: ''
                            env.GREEN_PROBABILITY = result.scheduling.green_probability?.toString() ?: ''
                            env.SCHEDULING_ACTION = result.scheduling.action ?: ''
                            env.SCHEDULING_ENGINE = result.scheduling.engine ?: ''
                            env.SCHEDULED_HOUR = result.scheduling.scheduled_hour?.toString() ?: ''
                            env.TARGET_INTENSITY = result.scheduling.target_intensity?.toString() ?: ''
                        } else {
                            env.CARBON_INTENSITY = ''
                            env.GREEN_PROBABILITY = ''
                            env.SCHEDULING_ACTION = ''
                            env.SCHEDULING_ENGINE = ''
                            env.SCHEDULED_HOUR = ''
                            env.TARGET_INTENSITY = ''
                        }
                    } else {
                        env.OPTIMIZER_STATUS = 'no_changes'
                        env.MAVEN_BUILD_COMMANDS = ''
                        env.MAVEN_TEST_COMMANDS = ''
                        env.AFFECTED_MODULES = ''
                    }

                    if (params.DRY_RUN) {
                        echo "=== DRY RUN MODE — Skipping build and test stages ==="
                    }
                }
            }
        }

        stage('Green Scheduling') {
            when {
                expression { params.ENABLE_GREEN_SCHEDULING && env.OPTIMIZER_STATUS == 'success' }
            }
            steps {
                script {
                    def targetHour
                    if (params.OVERRIDE_SCHEDULE_HOUR != 'auto') {
                        if (!params.OVERRIDE_SCHEDULE_HOUR.isInteger()) {
                            error("OVERRIDE_SCHEDULE_HOUR must be 'auto' or an integer from 0 to 23.")
                        }
                        targetHour = params.OVERRIDE_SCHEDULE_HOUR.toInteger()
                        if (targetHour < 0 || targetHour > 23) {
                            error("OVERRIDE_SCHEDULE_HOUR must be 'auto' or an integer from 0 to 23.")
                        }
                    } else if (env.SCHEDULING_ACTION == 'schedule' && env.SCHEDULED_HOUR?.isInteger()) {
                        targetHour = env.SCHEDULED_HOUR.toInteger()
                    }

                    if (targetHour != null) {
                        def delayHours = targetHour - new Date().getHours()
                        if (delayHours <= 0) {
                            delayHours += 24
                        }
                        def delaySeconds = delayHours * 3600
                        build job: env.JOB_NAME,
                              quietPeriod: delaySeconds,
                              wait: false,
                              parameters: [
                                  booleanParam(name: 'ENABLE_GREEN_SCHEDULING', value: false),
                                  booleanParam(name: 'DRY_RUN',                 value: params.DRY_RUN),
                                  booleanParam(name: 'FORCE_FULL_BUILD',        value: params.FORCE_FULL_BUILD),
                                  string(name: 'OVERRIDE_SCHEDULE_HOUR',        value: 'auto')
                              ]

                        currentBuild.description = "🌿 Build rescheduled for ${targetHour}:00 | ML Prob: ${env.GREEN_PROBABILITY ?: 'n/a'}"
                        currentBuild.result = 'ABORTED'
                        error("Build rescheduled to a greener window at ${targetHour}:00 to save carbon.")
                    }

                    echo "🌿 Build window confirmed. Proceeding with build and test."
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

                    // Calculate test counts from source inventory and Surefire reports.
                    def testsRun = 0
                    def testsSkipped = 0
                    def moduleDetails = [:]

                    def inventory = discoverModuleTestInventory()
                    def executed = readSurefireTestCounts()
                    def affected = affectedModuleSet()

                    greenReleaseModules().each { mod ->
                        def moduleTotal = inventory[mod] ?: 0
                        def moduleRun = affected.contains(mod) ? (executed[mod] ?: 0) : 0
                        def moduleSkipped = affected.contains(mod) ? 0 : moduleTotal
                        def status = affected.contains(mod) ? 'run' : 'skipped'

                        moduleDetails[mod] = ['status': status, 'run': moduleRun, 'skipped': moduleSkipped]
                        testsRun += moduleRun
                        testsSkipped += moduleSkipped
                    }
                    
                    env.TESTS_EXECUTED = testsRun.toString()
                    env.TESTS_SKIPPED = testsSkipped.toString()
                    env.MODULE_DETAILS = groovy.json.JsonOutput.toJson(moduleDetails).replaceAll('"', '\\\\"')
                    
                    echo "Total tests executed: ${env.TESTS_EXECUTED}"
                    echo "Total tests skipped: ${env.TESTS_SKIPPED}"
                    echo "Module Details: ${env.MODULE_DETAILS}"
                }
            }
        }

    }

    post {
        always {
            script {
                // Calculate total pipeline duration explicitly from our start time
                def totalDuration = (System.currentTimeMillis() - env.PIPELINE_START.toLong()) / 1000.0

                // If pipeline was skipped by optimizer, env.MODULE_DETAILS won't be set.
                if (!env.MODULE_DETAILS) {
                    def inventory = discoverModuleTestInventory()
                    def moduleDetails = [:]
                    def testsSkipped = 0
                    greenReleaseModules().each { mod ->
                        def count = inventory[mod] ?: 0
                        moduleDetails[mod] = ['status': 'skipped', 'run': 0, 'skipped': count]
                        testsSkipped += count
                    }
                    env.MODULE_DETAILS = groovy.json.JsonOutput.toJson(moduleDetails).replaceAll('"', '\\\\"')
                    env.TESTS_EXECUTED = env.TESTS_EXECUTED ?: '0'
                    env.TESTS_SKIPPED = env.TESTS_SKIPPED ?: testsSkipped.toString()
                }

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
                    "optimizer_duration_s": ${env.OPTIMIZER_DURATION ?: 'null'},
                    "modules_built": "${env.AFFECTED_MODULES ?: ''}",
                    "modules_tested": "${env.AFFECTED_MODULES ?: ''}",
                    "tests_executed": ${env.TESTS_EXECUTED ?: 0},
                    "tests_skipped": ${env.TESTS_SKIPPED ?: 0},
                    "module_details": "${env.MODULE_DETAILS ?: ''}",
                    "build_command": "${env.MAVEN_BUILD_COMMANDS ?: ''}",
                    "test_command": "${env.MAVEN_TEST_COMMANDS ?: ''}",
                    "carbon_intensity": ${env.CARBON_INTENSITY ?: 'null'},
                    "green_probability": ${env.GREEN_PROBABILITY ?: 'null'},
                    "scheduling_action": "${env.SCHEDULING_ACTION ?: ''}",
                    "scheduling_engine": "${env.SCHEDULING_ENGINE ?: ''}"
                }"""

                writeFile file: 'dashboard_payload.json', text: jsonPayload

                // Stop power monitor and attach carbon metrics to dashboard_payload.json
                def carbonIntensityForCalc = env.CARBON_INTENSITY ?: '475.0'
                sh """
                    scripts/power_monitor.sh stop power_monitor.pid || true
                    python3 scripts/calculate_carbon.py \\
                        --csv power_log.csv \\
                        --duration ${totalDuration} \\
                        --intensity ${carbonIntensityForCalc} \\
                        --attach-to dashboard_payload.json || true
                """

                sh """
                    curl -s -X POST ${DASHBOARD_URL}/api/builds \
                        -H "Content-Type: application/json" \
                        -d @dashboard_payload.json || true
                """
            }

        }
        success {
            script {
                if (params.DRY_RUN) {
                    echo "DRY RUN COMPLETE — Build #${BUILD_NUMBER} — Analysis only"
                } else if (env.OPTIMIZER_STATUS == 'success') {
                    echo "BUILD AND TEST SUCCESSFUL — Build #${BUILD_NUMBER} (modules: ${env.AFFECTED_MODULES})"
                } else {
                    echo "PIPELINE COMPLETE — Build #${BUILD_NUMBER} — No code changes to build (status: ${env.OPTIMIZER_STATUS})"
                }
            }
        }
        failure {
            echo "BUILD OR TEST FAILED — Build #${BUILD_NUMBER}"
        }
    }
}
