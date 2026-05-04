pipeline {
    agent any

    environment {
        // Docker Hub username/password credential (create in Jenkins)
        DOCKER_HUB_CREDENTIALS = credentials('dockerhub-credentials')
        // Set your registry namespace here (replace with your Docker Hub namespace)
        DOCKER_IMAGE = 'your-docker-namespace/green-release-app'
        DOCKER_TAG = "${BUILD_NUMBER}"

        // Remote server for deployment (update as needed)
        REMOTE_HOST = '147.15.144.192'
        REMOTE_PORT = '2510'
        REMOTE_USER = 'dumindu'
        SSH_CREDENTIALS = 'ubuntu-pc-ssh'

        // Optional metrics collector (adjust or remove)
        METRICS_URL = ''
    }

    stages {
        stage('Notify Start') {
            when { expression { env.METRICS_URL?.trim() }
            }
            steps {
                sh """
                    curl -s -X POST ${METRICS_URL}/deployment/start \
                        -H "Content-Type: application/json" \
                        -d '{"job_name":"${JOB_NAME}","build_number":"${BUILD_NUMBER}"}'
                """
            }
        }

        stage('Checkout') {
            steps {
                echo 'Checking out source from Git...'
                checkout scm
            }
        }

        stage('Build & Test') {
            steps {
                echo 'Building Maven multi-module project...'
                sh 'mvn -T1C -DskipTests clean package'
            }
        }

        stage('Docker Build') {
            steps {
                dir('app') {
                    echo "Building Docker image: ${DOCKER_IMAGE}:${DOCKER_TAG}"
                    sh "docker build -t ${DOCKER_IMAGE}:${DOCKER_TAG} -t ${DOCKER_IMAGE}:latest ."
                }
            }
        }

        stage('Docker Push') {
            steps {
                echo 'Pushing Docker image to registry'
                sh """
                    echo "${DOCKER_HUB_CREDENTIALS_PSW}" | docker login -u "${DOCKER_HUB_CREDENTIALS_USR}" --password-stdin
                    docker push ${DOCKER_IMAGE}:${DOCKER_TAG}
                    docker push ${DOCKER_IMAGE}:latest
                    docker logout
                """
            }
        }

        stage('Deploy to Server') {
            steps {
                sshagent(credentials: ["${SSH_CREDENTIALS}"]) {
                    sh "ssh -o StrictHostKeyChecking=no -p ${REMOTE_PORT} ${REMOTE_USER}@${REMOTE_HOST} 'mkdir -p /home/${REMOTE_USER}/green-release-demo'"

                    // copy docker-compose that references the pushed image
                    sh "scp -o StrictHostKeyChecking=no -P ${REMOTE_PORT} docker-compose.yml ${REMOTE_USER}@${REMOTE_HOST}:/home/${REMOTE_USER}/green-release-demo/docker-compose.yml"

                    sh "ssh -o StrictHostKeyChecking=no -p ${REMOTE_PORT} ${REMOTE_USER}@${REMOTE_HOST} 'set -e; cd /home/${REMOTE_USER}/green-release-demo; docker pull ${DOCKER_IMAGE}:latest; docker-compose down || true; docker-compose up -d; sleep 10; docker-compose ps'"
                }
            }
        }

        stage('Smoke Test') {
            steps {
                echo 'Running smoke test on remote host'
                sshagent(credentials: ["${SSH_CREDENTIALS}"]) {
                    sh "ssh -o StrictHostKeyChecking=no -p ${REMOTE_PORT} ${REMOTE_USER}@${REMOTE_HOST} 'curl -sf http://localhost:8080/health && echo SMOKE_TEST_PASSED || exit 1'"
                }
            }
        }
    }

    post {
        success {
            echo "Deployment SUCCESSFUL — Build #${BUILD_NUMBER}"
            script {
                if (env.METRICS_URL?.trim()) {
                    sh "curl -s -X POST ${METRICS_URL}/deployment/end -H 'Content-Type: application/json' -d '{"status":"SUCCESS","build_number":"${BUILD_NUMBER}","image":"${DOCKER_IMAGE}:${DOCKER_TAG}"}' || true"
                }
            }
        }
        failure {
            echo "Deployment FAILED — Build #${BUILD_NUMBER}"
            script {
                if (env.METRICS_URL?.trim()) {
                    sh "curl -s -X POST ${METRICS_URL}/deployment/end -H 'Content-Type: application/json' -d '{"status":"FAILURE","build_number":"${BUILD_NUMBER}"}' || true"
                }
            }
        }
        always {
            // cleanup local images
            sh "docker rmi ${DOCKER_IMAGE}:${DOCKER_TAG} || true"
            sh 'docker image prune -f || true'
        }
    }
}
