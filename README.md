akka-grid-workers
=================

Evaluation of different map-reduce like solutions in terms of their suitability for 
being used on a batch-computing system (such as BlueWaters @ NCSA)

For details, see [this post](https://groups.google.com/forum/#!topic/akka-user/5vYs4jKMaQU) on the [Akka User List forum](https://groups.google.com/forum/#!forum/akka-user)

To compile and deploy:

    sbt clean package
    # then copy the generated target/scala-x.y/*.jar to the [AKKA_HOME]/deploy/ folder of where Akka was installed
    # also copy everything from src/main/resources to [AKKA_HOME]/config/
    # and finally copy the logback-core, logback-classic, and scalalogging-slf4j jar dependencies to [AKKA_HOME]/lib/akka/

To run:

    # Start the Coordinator
    akka grid.CoordinatorMain
    
    # Start a Worker
    JAVA_OPTS="... -Dakka.cluster.seed-nodes.0=<actor path for coordinator>" akka grid.WorkerMain
    
    # Start the Producer
    JAVA_OPTS="... -Dakka.cluster.seed-nodes.0=<actor path for coordinator>" akka grid.ProducerMain
    
