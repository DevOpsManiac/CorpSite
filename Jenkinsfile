pipeline {
    agent any
    stages {
        stage('CI') {
            steps {
                snDevOpsStep()
                sh 'mvn compile'
                sh 'mvn verify'
            }
            post {
                success {
                    junit '**/target/surefire-reports/*.xml' 
                }
            }
        }
    
     stage('UAT deploy') {
            steps {
                snDevOpsStep()
               
                sh 'mvn package'
                sh 'mv -f target/globex-web.war /opt/tomcat/webapps/globex-uat.war'
                sh 'sudo systemctl stop tomcat9.service'
                sh 'echo ################ Reiniciando o Tomcat #############'
                sh 'sudo systemctl start tomcat9.service'
            
            }
        }
    }
}
    
