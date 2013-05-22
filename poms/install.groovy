#!/usr/bin/env groovy
/*
 * Copyright to the original author or authors.
 * Adapted for Lock Manager by Rafał Krupiński (C) 2013
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
String version = "0.2"
String rootDir = ".."

[
"org.sorcersoft.dancres:lockmgr":"lib",
"org.sorcersoft.dancres:lockmgr-dl":"lib"
].each {artifact, subDir ->

    String[] parts = artifact.split(":")
    String gId = parts[0]
    String aId = parts[1]
    String dir = rootDir+"/"+subDir
    String installCommand = "mvn install:install-file "+
                            "-Dversion=${version} "+
                            "-DgeneratePom=false -Dpackaging=jar "+
                            "-DgroupId=${gId} "+
                            "-DartifactId=${aId} "+
                            "-Dfile=${dir}/${aId}.jar "+
                            "-DpomFile=${aId}.pom"
    println installCommand
    Process process = installCommand.execute()
    process.consumeProcessOutputStream(System.out)
    process.consumeProcessErrorStream(System.err)
    process.waitFor()
}
