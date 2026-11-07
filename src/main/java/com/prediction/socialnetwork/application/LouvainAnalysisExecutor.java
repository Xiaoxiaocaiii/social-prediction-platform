package com.prediction.socialnetwork.application;

import com.prediction.socialnetwork.isolation.LouvainWorkerRunResult;
import com.prediction.socialnetwork.network.LouvainAnalysisParameters;
import com.prediction.socialnetwork.network.NetworkEdge;
import com.prediction.socialnetwork.network.NetworkNode;
import java.util.List;
import java.util.function.BooleanSupplier;

public interface LouvainAnalysisExecutor {

    LouvainWorkerRunResult run(
            List<NetworkNode> nodes,
            List<NetworkEdge> directedEdges,
            LouvainAnalysisParameters parameters,
            BooleanSupplier cancellationRequested);
}
