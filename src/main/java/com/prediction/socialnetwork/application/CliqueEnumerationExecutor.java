package com.prediction.socialnetwork.application;

import com.prediction.socialnetwork.network.CliqueEnumerationResult;
import com.prediction.socialnetwork.network.NetworkEdge;
import com.prediction.socialnetwork.network.NetworkNode;
import java.util.List;
import java.util.function.BooleanSupplier;

@FunctionalInterface
public interface CliqueEnumerationExecutor {

    CliqueEnumerationResult run(
            List<NetworkNode> nodes,
            List<NetworkEdge> directedEdges,
            BooleanSupplier cancellationRequested);
}
