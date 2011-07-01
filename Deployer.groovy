/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 * 		http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

/**
 * Utility script to facilitate deployment to a multiple Windows based 
 * tomcat instances running as services. 
 * 
 * @author Nigel Garner
 */
// TODO Use cached files if not dev build
// TODO Check environment config exists
// TODO Archive the previous logs
def ant = new AntBuilder()

// -----------------------------------------------------
// --------------- Command Line Usage  -----------------
// -----------------------------------------------------
def cli = new CliBuilder(usage: 'deployer.groovy -[ho] [environment] [version]')

// Create the list of options.
cli.with {
	h longOpt: 'help', 'Show usage information'
	o longOpt: 'offline', 'Work offline'
}

// Parse the passed command line args
def options = cli.parse(args)
// Check the extra args
def extraArgs = options.arguments()

// Show usage text when -h or --help option is used or we are missing arguements.
if (options.h || extraArgs.size != 2) {
	cli.usage()
	return
}

// Assign the args accordingly
envName = extraArgs[0]
version = extraArgs[1]

// -----------------------------------------------------
// --------------- Initialise/Configure  ---------------
// -----------------------------------------------------

// Dynamic configs
def config = new ConfigSlurper("global").parse(new File('Environments.groovy').toURI().toURL())
def environment = new ConfigSlurper(envName).parse(new File('Environments.groovy').toURI().toURL())

def repository = config.global.repository
def tomcat = config.global.tomcat;
def installDir = environment.installation.dir;
def devBuildSuffix = config.global.build.snapshot.suffix

// Computed configs
def formalVersion

// Find the version details
if(version.endsWith(devBuildSuffix)) {
	formalVersion = version.tokenize(devBuildSuffix).get(0)
} else {
	formalVersion = version
}

def archiveDir = "${config.global.application.archive.base}/${formalVersion}"
def backupDir = "${environment.installation.backup}/${formalVersion}"
def deployableFile = "${archiveDir}/${environment.artifact.id}-${version}.${environment.artifact.type}"

def offline 

if(options.o) {
	offline = true
} else {
	offline = !environment.artifact.download 
} 


// -----------------------------------------------------
// --------------- Start doing some work ---------------  
// -----------------------------------------------------

// -- Prep ------
ant.mkdir(dir:"${archiveDir}")

// -- Download ------
// Check if the file is to be downloaded
if(!offline) {

	// TODO Switch to Grape? - No as it downloads full dependencies and not just that artifact. Need to check config options
	//groovy.grape.Grape.grab([group:"${environment.artifact.group}", module:"${environment.artifact.id}", version:"${version}", ext:"war"])
	
	// Convert the maven groupId format to a url style
	def groupUrlFormat = "${environment.artifact.group}".tr('.','/')
	// Build the URL based on m2 format
	def url = "${repository.repo}/${groupUrlFormat}/${environment.artifact.id}/${version}/${environment.artifact.id}-${version}.${environment.artifact.type}"
	
	// Fetch the file from and store it in the cache
	ant.get(src:"${url}", dest:"${archiveDir}", verbose:'true', username:"${repository.username}", password:"${repository.password}")
}

// -- Shutdown ------
// Shutdown the service
println "Stopping Service ${environment.tomcat.service.name}"
println "Executing ${environment.tomcat.stop}"
"${environment.tomcat.stop}".execute()

// -- Backup ------
println "Backing up existing deployment"
// Remove the old backup
ant.delete(dir:"${backupDir}", verbose:'true')
// Create the backup folder
ant.mkdir(dir:"${backupDir}")
// If previous deployment exists then back it up
if((new File(installDir)).exists()) {
	ant.move(file:"${installDir}", todir:"${backupDir}", verbose:'true')
}

// -- Deploy ------
println "Deploying ${deployableFile}"
// Make the deployment dir
ant.mkdir(dir:"${installDir}")
// Extract the archive
ant.unwar(src:"${deployableFile}", dest:"${installDir}")

// -- Post Deploy ------
def postDeployHandler = new PostDeployHandler();
postDeployHandler.performFiltering environment, installDir

// -- Start ------
// Start the service
println "Starting Service ${environment.tomcat.service.name}"
println "Executing ${environment.tomcat.start}"
"${environment.tomcat.start}".execute()
