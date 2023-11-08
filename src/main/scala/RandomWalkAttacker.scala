package com.natalia

import org.apache.spark.*
import org.apache.spark.graphx.*
import org.apache.spark.rdd.RDD
import NetGraphAlgebraDefs.NodeObject
import NetModelAnalyzer.TerminationPolicy

import scala.util.Random
import com.lsc.Main.logger

import scala.collection.mutable

class RandomWalkAttacker(private val howManyWalks: Int,
                         private val maxNumSteps: Int,
                         private val originalValuableNodes: List[VertexId]
                        ) extends Serializable {
  require(howManyWalks > 0, "Number of walks must be positive")

  type Path = List[VertexId]

  // Function to perform random walks using GraphX
  def walkAndAttack(perturbedGraphX: Graph[NodeObject, Double],
                    originalGraphX: Graph[NodeObject, Double],
                    sc: SparkContext
                   ): List[Path] = {
    logger.info("RandomWalkAttacker - starting walkAndAttack")
    // Get a random subset of vertices as starting points
    val randomInitialVertices = getRandomInitialVertices(perturbedGraphX, sc)
    val broadcastPerturbedGraphX = sc.broadcast(perturbedGraphX)
    val broadcastOriginalGraphX = sc.broadcast(originalGraphX)

    // Create an RDD of starting vertices
    val startingVerticesRDD = sc.parallelize(randomInitialVertices.toSeq)

    // Function to perform a single random walk from a starting vertex
    def performSingleWalk(initialVertex: VertexId): Path = {
      logger.info(s"RandomWalkAttacker - PERFORMING SINGLE WALK WITH INITIAL VERTEX ${initialVertex}")
      val perturbedGraph = broadcastPerturbedGraphX.value
      val originalGraph = broadcastOriginalGraphX.value
      val initialPath = List(initialVertex)

      val walkResult = performWalkFromVertex(
        initialVertex,
        initialPath,
        perturbedGraph,
        originalGraph,
      )
      walkResult
    }

    // Perform the random walks in parallel
    val walkResultsRDD = startingVerticesRDD.map(performSingleWalk)
    val results = walkResultsRDD.collect().toList

    broadcastPerturbedGraphX.unpersist()
    broadcastOriginalGraphX.unpersist()
    results
  }

  // Sample code for obtaining random initial vertices
  private def getRandomInitialVertices(perturbedGraphX: Graph[NodeObject, Double],
                                       sc: SparkContext
                                      ): Set[VertexId] = {
    val broadcastPerturbedGraphX = sc.broadcast(perturbedGraphX)
    val allVertices = broadcastPerturbedGraphX.value.vertices.map(_._1).collect()
    //    val randomInitialVertices = Random.shuffle(allVertices.toList).take(howManyWalks)
    val randomInitialVertices = Random.shuffle(allVertices.toList).filter(vertexId => vertexId == 11L)
    broadcastPerturbedGraphX.unpersist()
    logger.info(s"RandomWalkAttacker - random initial vertices: $randomInitialVertices")
    randomInitialVertices.toSet
  }

  // Function to perform a single random walk
  def performWalkFromVertex(originalStartingVertex: VertexId,
                            path: Path,
                            perturbedGraphX: Graph[NodeObject, Double],
                            originalGraphX: Graph[NodeObject, Double]
                           ): Path = {
    val stack = mutable.Stack((path, path.head))

    while (stack.nonEmpty) {
      val (currentPath, currentVertex) = stack.pop()
      logger.info(
        s"RandomWalkAttacker - VISITING VERTEX ${currentVertex} IN WALK WITH CURRENT PATH ${currentPath}"
      )

      // Calculate SimRank scores and check for attacks
      val simRankResults = SimRank.calculateSimRankAgainstEachValuableNode(
        currentVertex,
        originalValuableNodes,
        perturbedGraphX,
        originalGraphX
      )

      simRankResults.foreach((simRankResult) => {
        val simRankScore = simRankResult.simRankScore
        val simRankValuableNodeId = simRankResult.originalValuableNodeId
        if (simRankScore >= Configuration.getSimRankThreshold) {
          logger.info(
            s"RandomWalkAttacker - Attacking node $currentVertex in the perturbed graph (SimRank Score to node ${simRankValuableNodeId} is: $simRankScore)"
          )
          if (originalValuableNodes.contains(currentVertex)) {
            logger.info(s"RandomWalkAttacker - Attacking perturbed node $currentVertex SUCCEEDED")
          } else {
            logger.error(s"RandomWalkAttacker - Attack perturbed node $currentVertex FAILED")
          }
        }
      })

      if (currentPath.length >= maxNumSteps) {
        logger.info(s"RandomWalkAttacker - REACHED TERMINATION POINT WHERE MAX NUM STEPS REACHED: ${maxNumSteps}")
        return currentPath.reverse
      }

      val neighbors = perturbedGraphX.edges
        .filter(edge => edge.srcId == currentVertex)
        .map(edge => edge.dstId)
        .collect()

      if (neighbors.isEmpty) {
        logger.info("RandomWalkAttacker - REACHED TERMINATION POINT WHERE WE'VE REACHED TERMINAL NODE")
        return currentPath.reverse
      }

      val nextVertexToVisit = neighbors(Random.nextInt(neighbors.length))

      // Push the new state onto the stack
      stack.push((nextVertexToVisit :: currentPath, nextVertexToVisit))
    }

    // If the stack becomes empty, the walk terminates
    path.reverse
  }
}
