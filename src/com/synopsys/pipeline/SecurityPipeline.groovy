#! /bin/groovy

package com.synopsys.pipeline

import java.nio.channels.Pipe

/**
 * This script is the entry point for the pipeline shared library.
 * It defines pipeline stages and manages overall control flow in the pipeline.
 */

def execute() {
    node() {

        stage('Checkout Code') {
            git branch: env.GIT_BRANCH, url: env.GIT_URL
        }

        stage('Building Source Code') {  
            sh env.BUILD_COMMAND
            echo 'build source code'
        }

        stage('IO - Setup Prescription') {
            echo 'Setup Prescription'
            synopsysIO(connectors: [io(configName: env.IO_PROJECT, projectName: env.GIT_PROJECT, workflowVersion: env.WORKFLOW_VERSION), 
                                    github(branch: env.GIT_BRANCH, configName: env.GIT_USR_PROJECT, owner: env.GIT_USER, repositoryName: env.GIT_PROJECT), 
                                    rapidScan(configName: 'Sigma')]) {
                sh 'io --stage io'
            }
        }

        /*stage('IO - Read Prescription') {
            print("End REST API request to IO")
            def PrescriptionJson = readJSON file: 'io_state.json'
            print("Updated PrescriptionJson JSON :\n$PrescriptionJson\n")
            print("Sast Enabled : $PrescriptionJson.Data.Prescription.Security.Activities.Sast.Enabled")
        }*/

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
