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
                
                script {
                    sshPublisher(continueOnError: false, failOnError: true,
                    publishers: [
                        sshPublisherDesc(
                            configName:'CorpSite UAT',
                            verbose: true,
                            transfers: [
                                sshTransfer(
                                    sourceFiles: 'target/globex-web.war',
                                    removePrefix: 'target/',
                                    remoteDirectory: '/opt/tomcat/webapps'
                                )])])
                                 }
                }
    
    




        
    }
  }
}
   
