# APIServer Daemon
[![Travis](http://img.shields.io/travis/FutureGateway/geAPIServer/master.png)](https://travis-ci.org/FutureGateway/geAPIServer)
[![License](https://img.shields.io/github/license/FutureGateway/geAPIServer.svg?style?flat)](http://www.apache.org/licenses/LICENSE-2.0.txt)

This component phisically executes instructions provided by the RESTful APIs Server in compliancy with [CSGF APIs][specs] specifications.

This service consumes a queue of commands prepared by the API Server [front-end][fgAPIServer]. This daemon is implemented as a web application which internally has a polling thread to manage incoming commands from the API Server front-end. Each command is passed to a different class (Executor interfaces) accordingly to the targeted infrastructure involved by the command.
This daemon foresees the followng main features:

 - It exploits the [CSGF][CSGF]' GridEngine system to target distributed ifnrastructures such as: Grid (EMI/gLite), Cloud (rOCCI), HPC (ssh)
 - It supports other executor services just providing the correct interface class
 - This daemon extract commans enqued by the API Server [front-end][fgAPIServer]
 - The API agent manages incoming REST calls and then instructs the targeted executor interface accordingly

   [specs]: <http://docs.csgfapis.apiary.io/#reference/v1.0/application/create-a-task>
   [CSGF]: <https://www.catania-science-gateways.it>
   [fgAPIServer]: <https://github.com/FutureGateway/fgAPIServer>
