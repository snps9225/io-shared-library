#! /bin/groovy

package com.synopsys.pipeline

import java.nio.channels.Pipe

/**
 * This script is the entry point for the pipeline shared library.
 * It defines pipeline stages and manages overall control flow in the pipeline.
 */

def execute() {
    node() {
        
        properties([parameters([ 
            string(name: "branch_name", defaultValue: "main", trim: true, description: "Branch name of the project"),
            string(name: "url", defaultValue: "https://", trim: true, description: "Git URL of the project"),
            string(name: "build_command", defaultValue: "mvn ", description: "Code build command")]
        )])

        stage('Checkout Code') {
            git branch: $params.branch_name, url: $params.url
        }

        stage('Building Source Code') {  
            sh '''$params.build_command'''
            echo 'build source code'
        }

        stage('IO - Setup Prescription') {
            echo 'Setup Prescription'
            synopsysIO(connectors: [io(configName: 'io-training', projectName: 'insecure-bank', workflowVersion: '2021.12.2'), 
                                    github(branch: $params.branch_name, configName: 'snps9225/insecure-bank', owner: 'snps9225', repositoryName: 'insecure-bank'), 
                                    rapidScan(configName: 'Sigma')]) {
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

        stage('IO - Workflow') {
            echo 'Execute Workflow Stage'
            synopsysIO() {
                    sh 'io --stage workflow --state io_state.json'
            }
        }

        stage('IO - Archive') {
            archiveArtifacts artifacts: '**/*-results*.json', allowEmptyArchive: 'true'
            archiveArtifacts artifacts: '**/*-report*.*', allowEmptyArchive: 'true'
            //remove the state json file it has sensitive information
            //sh 'rm io_state.json'
        }
    }
}
