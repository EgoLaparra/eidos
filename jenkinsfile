pipeline {
  agent any
  stages {
    stage('') {
      steps {
        sh '''pipeline {
    agent any

    stages {

        stage(\'Test\') {
	            steps {
			  echo "Testing..."
			  sh "sbt test"
		}
	}
    }
}'''
        }
      }
    }
  }