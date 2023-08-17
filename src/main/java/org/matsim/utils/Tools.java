package org.matsim.utils;

import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.algorithms.MultimodalNetworkCleaner;
import org.matsim.core.network.algorithms.NetworkCleaner;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Tools {
    public static void downloadFile(String fileUrl, String savePath) throws IOException {
        InputStream in = new URL(fileUrl).openStream();
        Files.copy(in, Paths.get(savePath), StandardCopyOption.REPLACE_EXISTING);
    }

    public static void removeLinksFromNetwork(Network network, List<Link> linksToRemove) {
        for (Link link : linksToRemove) {
            network.removeLink(link.getId());
        }

        // Remove empty nodes
        Set<Node> nodesToRemove = new HashSet<>();
        for (Node node : network.getNodes().values()) {
            if (node.getInLinks().isEmpty() && node.getOutLinks().isEmpty()) {
                nodesToRemove.add(node);
            }
        }
        for (Node node : nodesToRemove) {
            network.removeNode(node.getId());
        }

        // Clean the network
        NetworkCleaner networkCleaner = new NetworkCleaner();
        networkCleaner.run(network);
    }

}
