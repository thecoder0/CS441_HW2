# Homework 2

#### Natalia Szlaszynski
#### University of Illinois at Chicago - CS 441
#### nszlas2@uic.edu

#### The main goal of this assignment is to simulate attacks and generate statistics for a Man-in-the-Middle (MitM) attacker. This is accomplished by employing graphs and their perturbed versions through a cloud-based pipelined execution process. The simulation will involve parallel random walks, modeling an inside attacker within a large enterprise network whose objective is to investigate network nodes.
## Overview
#### • Random Walk - Used to model deterministic situations. In our case, this involves randomly selecting the next node connected by an edge to the current node based on a random variable. These random selections of nodes and edges, initiated from a starting node, form a path in the graph, constituting an instance of a random walk.
#### • Simrank - The program usees a SimRank score between a perturbed node that is visited and every valuable original node from the original graph. If a SimRank score against an original valuable node is above a configured threshold, the program decides to attack the node and logs whether the atack was successful or not.
#### • The attribute _ValuableData_ for a node in the original graph indicates whether a specific computer contains sensitive data.
####       • An attack is deemed successful only when the attacker targets this node in the perturbed graph.
####       • An attack is deemed a failure when the attacker attacks a "honeypot" node.


## Dependencies
#### Netgamesim - https://github.com/0x1DOCD00D/NetGameSim (Owned by Dr. Mark Grechanik)
## Quick Setup
#### • In the local terminal, run the following commands:
#### ```git clone <link_to_my_repo>```
#### ```cd``` into the repo and run ```sbt clean compile run``` to run the program (the original and perturbed graph files are already provided in the src/main/resources folder)