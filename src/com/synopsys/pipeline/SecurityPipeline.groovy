#! /bin/groovy

package com.synopsys.pipeline

import java.nio.channels.Pipe

/**
 * This script is the entry point for the pipeline shared library.
 * It defines pipeline stages and manages overall control flow in the pipeline.
 */

def execute() {
    node('master') {

        stage('Checkout Code') {
            cleanWs()
            sh 'apt-get install -y unzip'
            git branch: 'production', url: 'https://github.com/devsecops-test/github-io-sample'
        }

        stage('Building Source...') {
            //your build command, your way here...    
            //sh '''mvn clean compile'''
            echo 'build source code'
        }

        stage('IO - Setup Prescription') {
            echo 'Setup Prescription'
            synopsysIO(connectors: [io(configName: 'iostaging', projectName: 'github-io-sample', workflowVersion: '2021.12.0.10-alpha'),
                github(branch: 'production', configName: 'github-io-sample', owner: 'devsecops-test', repositoryName: 'github-io-sample'),
                buildBreaker(configName: 'BB-ALL')
            ]) {
                sh 'io --stage io'
            }
        }

        stage('IO - Read Prescription') {
            print("End REST API request to IO")
            def PrescriptionJson = readJSON file: 'io_state.json'
            print("Updated PrescriptionJson JSON :\n$PrescriptionJson\n")
            print("Sast Enabled : $PrescriptionJson.Data.Prescription.Security.Activities.Sast.Enabled")
        }

        stage('SAST- RapidScan') {
            environment {
                OSTYPE = 'linux-gnu'
            }

            echo 'Running SAST using Sigma - Rapid Scan'
            echo env.OSTYPE
            synopsysIO(connectors: [rapidScan(configName: 'Sigma')]) {
                sh 'io --stage execution --state io_state.json'
            }
        }
        
        stage('SAST- Spotbugs') {
            sh 'wget "https://raw.githubusercontent.com/synopsys-sig/io-client-adapters/main/spotbugs/spotbugs.sh"'
            sh 'wget "https://raw.githubusercontent.com/synopsys-sig/io-client-adapters/main/spotbugs/spotbugs-adapter.json"'
            echo 'Running SAST using Spotbugs'
            synopsysIO() {
                sh 'io --stage execution --adapters spotbugs-adapter.json --state io_state.json'
            }
        }

        stage('SAST - Polaris') {
            echo 'Running SAST using Polaris'
            synopsysIO(connectors: [
                [$class: 'PolarisPipelineConfig', configName: 'csprod-polaris', projectName: 'sig-devsecops/github-io-sample']
            ]) {
                sh 'io --stage execution --state io_state.json'
            }
        }

        stage('IO - Workflow') {
            echo 'Execute Workflow Stage'
            synopsysIO() {
                synopsysIO() {
                    sh 'io --stage workflow --state io_state.json'
                }
            }
        }

        stage('IO - Archive') {
            //archiveArtifacts artifacts: '**/*-results*.json', allowEmptyArchive: 'true'
            archiveArtifacts '**/*-results*.json'
            //remove the state json file it has sensitive information
            //sh 'rm io_state.json'
        }
    }
}
