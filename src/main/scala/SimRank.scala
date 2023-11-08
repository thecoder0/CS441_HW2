package com.natalia

import NetGraphAlgebraDefs.NodeObject
import org.apache.spark.graphx.{EdgeDirection, Graph, VertexId}
import com.lsc.Main.logger

object SimRank {
  // Define a case class to represent SimRank results
  case class SimRankResult(originalValuableNodeId: VertexId, simRankScore: Double)

  /** For each valuable node N from the original graph, compute a SimRank score
   * between the node from the perturbed graph and that node N to accumulate a list
   * of SimRankResult objects containing node N and the SimRank score
   *
   * @param perturbedNode         The node from the perturbed graph.
   * @param originalValuableNodes The list of valuable nodes from the original graph.
   * @param perturbedGraphX       The perturbed GraphX object.
   * @param originalGraphX        The original GraphX object.
   * @return A list of SimRankResult objects
   */
  def calculateSimRankAgainstEachValuableNode(perturbedNode: VertexId,
                                              originalValuableNodes: List[VertexId],
                                              perturbedGraphX: Graph[NodeObject, Double],
                                              originalGraphX: Graph[NodeObject, Double]
                                             ): List[SimRankResult] = {
    logger.info(
      s"SimRank - Calculating SimRank between perturbed node $perturbedNode and every node in originalValuableNodes: $originalValuableNodes"
    )
    originalValuableNodes.map(originalValuableNode => {
      val simRankScore = calculateSimRankBetweenTwoNodes(
        perturbedNode,
        originalValuableNode,
        perturbedGraphX,
        originalGraphX
      )

      SimRankResult(originalValuableNode, simRankScore)
    })
  }

  // Function to calculate SimRank between two nodes
  def calculateSimRankBetweenTwoNodes(perturbedNode: VertexId,
                                      originalNode: VertexId,
                                      perturbedGraphX: Graph[NodeObject, Double],
                                      originalGraphX: Graph[NodeObject, Double]
                                     ): Double = {
    logger.info(
      s"SimRank - Calculating SimRank between PERTURBED node $perturbedNode and ORIGINAL node $originalNode"
    )
    val decayControl = Configuration.getSimRankDecayControl // A parameter between 0 and 1 that controls the decay of similarity with distance
    val maxDepth = Configuration.getSimRankMaxDepth // A parameter that controls the maximum depth for SimRank calculation

    // Find common ancestors using a map/reduce approach
    val commonAncestorPaths = findCommonAncestorPaths(perturbedGraphX, originalGraphX, perturbedNode, originalNode, maxDepth)

    if (commonAncestorPaths.isEmpty) {
      logger.info(s"SimRank - PERTURBED node $perturbedNode and ORIGINAL node $originalNode have 0 common ancestor paths")
      return 0.0 // Return 0 if there are no common ancestors
    }

    logger.info(s"SimRank - PERTURBED node $perturbedNode and ORIGINAL node $originalNode DO have common ancestor paths: $commonAncestorPaths")

    // Calculate the SimRank score for each ancestor path
    val simRankScores = commonAncestorPaths.map { ancestorPath =>
      calculateSimRankFromCommonAncestorPath(ancestorPath, decayControl, perturbedGraphX, originalGraphX)
    }

    val sum = simRankScores.sum
    val finalSimRankScore = sum / commonAncestorPaths.length.toDouble

    logger.info(s"SimRank - FINAL SimRank score between PERTURBED node $perturbedNode and original node $originalNode is: $finalSimRankScore")

    finalSimRankScore
  }

  // Function to find common ancestor paths using a map/reduce approach
  def findCommonAncestorPaths(perturbedGraphX: Graph[NodeObject, Double],
                              originalGraphX: Graph[NodeObject, Double],
                              perturbedNode: VertexId,
                              originalNode: VertexId,
                              maxDepth: Int
                             ): List[List[VertexId]] = {
    logger.info(s"SimRank - Performing bfsCommonAncestorPaths between PERTURBED node $perturbedNode and ORIGINAL node $originalNode")

    val perturbedAncestorPaths = findAncestorPaths(perturbedGraphX, perturbedNode, maxDepth)
    logger.info(s"SimRank - findCommonAncestorPaths - PERTURBED node $perturbedNode ancestor paths: $perturbedAncestorPaths")
    val originalAncestorPaths = findAncestorPaths(originalGraphX, originalNode, maxDepth)
    logger.info(s"SimRank - findCommonAncestorPaths - ORIGINAL node $originalNode ancestor paths: $originalAncestorPaths")

    val commonAncestorPaths = perturbedAncestorPaths.intersect(originalAncestorPaths).toList
    logger.info(s"SimRank - findCommonAncestorPaths - ORIGINAL node $perturbedNode common ancestor paths: $commonAncestorPaths")
    commonAncestorPaths
  }

  // Function to find ancestors using a map/reduce approach
  def findAncestorPaths(graphX: Graph[NodeObject, Double], node: VertexId, maxDepth: Int): List[List[VertexId]] = {
    (0 to maxDepth)
      .foldLeft(List(List(node))) { (ancestors, currentDepth) =>
        val newAncestors = ancestors
          .flatMap(path => {
            logger.info(s"SimRank - findAncestorPaths - current path is $path")
            val neighbors = graphX.collectNeighborIds(EdgeDirection.In).lookup(path.head).head.toList
            val validNeighbors = neighbors.filter(neighbor => !path.contains(neighbor))
            // Add logging here to print intermediate results
            logger.info(s"SimRank - findAncestorPaths - Depth $currentDepth - Path: $path, Neighbors: $validNeighbors")
            validNeighbors.map(neighbor => path :+ neighbor)
          })
        (ancestors.toSet ++ newAncestors.toSet).toList
      }
  }

  // Function to calculate SimRank from a path
  def calculateSimRankFromCommonAncestorPath(commonAncestorPath: List[VertexId],
                                             decayControl: Double,
                                             perturbedGraphX: Graph[NodeObject, Double],
                                             originalGraphX: Graph[NodeObject, Double]): Double = {
    if (commonAncestorPath.isEmpty) {
      return 0.0 // No common ancestors, return a similarity score of 0
    }

    logger.info(s"SimRank - calculateSimRankFromPath - calculating SimRank from path $commonAncestorPath")

    // Calculate SimRank based on the number of common neighbors in both graphs
    val numCommonNeighbors = commonNeighborsCount(commonAncestorPath, perturbedGraphX, originalGraphX)

    if (numCommonNeighbors == 0) {
      return 0.0 // If there are no common neighbors, return 0
    }

    // Calculate the SimRank score based on the number of common neighbors and the decay parameter c
    val simRank = Math.pow(decayControl, commonAncestorPath.length) / numCommonNeighbors

    logger.info(s"SimRank - calculateSimRankFromPath - SimRank score for path $commonAncestorPath is $simRank - ")

    simRank
  }

  // Function to count common neighbors in both graphs
  def commonNeighborsCount(path: List[VertexId], perturbedGraphX: Graph[NodeObject, Double], originalGraphX: Graph[NodeObject, Double]): Int = {
    // Initialize the common neighbor count
    var commonCount = 0

    // Iterate over nodes in the path
    for (node <- path) {
      val neighborsPerturbed = perturbedGraphX.collectNeighborIds(EdgeDirection.Either).lookup(node).head.toSet
      val neighborsOriginal = originalGraphX.collectNeighborIds(EdgeDirection.Either).lookup(node).head.toSet

      // Count the common neighbors for the current node
      val commonNeighbors = neighborsPerturbed.intersect(neighborsOriginal)
      logger.info(s"SimRank - commonNeighborsCount - node $node has these neighbors in both graphs: $commonNeighbors")

      // Add the count of common neighbors for this node to the total count
      commonCount += commonNeighbors.size
    }

    logger.info(s"SimRank - commonNeighborsCount - common neighbors count for path $path is $commonCount")

    commonCount
  }

}