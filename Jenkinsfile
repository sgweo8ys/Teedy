pipeline {
    agent any

    environment {
        DOCKER_HUB_CREDENTIALS = credentials('dockerhub_credentials') // 凭证ID
        DOCKER_IMAGE = 'sgweo8ys/teedy-app' // 替换为你的Docker Hub仓库名
        DOCKER_TAG = "${env.BUILD_NUMBER}"
    }

    stages {
        // 构建项目
        stage('Build') {
            steps {
                checkout scmGit(
                    branches: [[name: '*/master']],
                    userRemoteConfigs: [[url: 'https://github.com/sgweo8ys/Teedy.git']] // 替换为你的仓库URL
                )
                sh 'mvn -B -DskipTests clean package'
            }
        }

        // 构建Docker镜像
        stage('Build Image') {
            steps {
                script {
                    docker.build("${env.DOCKER_IMAGE}:${env.DOCKER_TAG}")
                }
            }
        }

        // 推送镜像到Docker Hub
        stage('Push Image') {
            steps {
                script {
                    docker.withRegistry('https://registry.hub.docker.com', env.DOCKER_HUB_CREDENTIALS) {
                        docker.image("${env.DOCKER_IMAGE}:${env.DOCKER_TAG}").push()
                        docker.image("${env.DOCKER_IMAGE}:${env.DOCKER_TAG}").push('latest')
                    }
                }
            }
        }

        // 运行三个容器（8082、8083、8084）
        stage('Run Containers') {
            steps {
                script {
                    // 定义端口列表
                    def ports = [8082, 8083, 8084]

                    ports.each { port ->
                        // 停止并删除旧容器
                        sh "docker stop teedy-container-${port} || true"
                        sh "docker rm teedy-container-${port} || true"

                        // 运行新容器
                        docker.image("${env.DOCKER_IMAGE}:${env.DOCKER_TAG}").run(
                            "--name teedy-container-${port} -d -p ${port}:8080"
                        )
                    }

                    // 验证容器状态
                    sh 'docker ps --filter "name=teedy-container"'
                }
            }
        }
    }
}