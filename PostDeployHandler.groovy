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
 * Handler to manage any post deployment tasks required such as resource filtering
 * 
 * @author Nigel Garner
 */
public class PostDeployHandler {

	/**
	 * Performs filtering of resources based upon the filter configuration defined in the Environments file
	 * @param configuration
	 * @param installDirectory
	 */
	public void performFiltering(def configuration, def installDirectory){
		// -- Load the Configuration ------
		// Get the Post Deploy Filters
		Map filters = configuration.getProperty("postdeploy").getProperty("filters");
		
		// Initialise the List to hold the resulting config objects
		def filterConfigs = []
		
		// For each key in the config map
		filters.keySet().each{ key ->
			// Get the entry
			def entry = filters.get(key)
			// Define the dynamic config objecy
			def configItem = new Expando()
			// Load the config
			configItem.file = "${entry.file}"
			configItem.regex = "${entry.regex}"
			configItem.replace = "${entry.replace}"
			// Add it to the List
			filterConfigs.add(configItem)
		}
		
		println "Performing filtering against configuration ${filterConfigs}"
		
		
		// -- Perform the filtering ------
		def fileText
		
		// Load each matching file
		new File( installDirectory ).eachFileRecurse { file ->
			filterConfigs.each {filter ->
				if(file.name =~ filter.file) {
					println "Matched file ${file.name}, replacing ${filter.regex} with ${filter.replace}"
					// Load the text from the file
					fileText = file.text
					// Replace all instances of the strings
					fileText = fileText.replaceAll(filter.regex, filter.replace)
					// Write the response
					file.write(fileText)
				}
			}
		}
	}
}


