package org.matsim.source;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.locationtech.jts.geom.Geometry;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.*;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.ShpOptions;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.utils.geometry.geotools.MGC;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class GeneratePlansFromTripFile implements MATSimAppCommand {
    @CommandLine.Option(names = "--trips", description = "path to input drt legs csv file", required = true)
    private Path inputPlansPath;

    @CommandLine.Option(names = "--network", description = "path to drt network", defaultValue = "")
    private String networkPath;

    @CommandLine.Option(names = "--output", description = "output plans path", required = true)
    private Path outputPath;

    @CommandLine.Mixin
    private ShpOptions shp = new ShpOptions();

    public static void main(String[] args) {
        new GeneratePlansFromTripFile().execute(args);
    }

    @Override
    public Integer call() throws Exception {
        Network network = NetworkUtils.readNetwork(networkPath);
        Population outputPopulation = PopulationUtils.createPopulation(ConfigUtils.createConfig());
        PopulationFactory populationFactory = outputPopulation.getFactory();

        // We don't want the request to start on very long links
        List<Link> linksToRemove = new ArrayList<>();
        for (Link link : network.getLinks().values()) {
            if (link.getLength() >= 1000) {
                linksToRemove.add(link);
            }
        }
        linksToRemove.forEach(link -> network.removeLink(link.getId()));

        List<Node> nodesToRemove = new ArrayList<>();
        for (Node node : network.getNodes().values()) {
            if (node.getOutLinks().isEmpty() && node.getOutLinks().isEmpty()) {
                nodesToRemove.add(node);
            }
        }
        nodesToRemove.forEach(node -> network.removeNode(node.getId()));

        int counter = 0;
        try (CSVParser parser = new CSVParser(Files.newBufferedReader(inputPlansPath),
                CSVFormat.DEFAULT.withDelimiter(';').withFirstRecordAsHeader())) {
            for (CSVRecord record : parser.getRecords()) {
                double departureTime = Double.parseDouble(record.get(0));
                Coord fromCoord = new Coord(Double.parseDouble(record.get(4)), Double.parseDouble(record.get(5)));
                Coord toCoord = new Coord(Double.parseDouble(record.get(7)), Double.parseDouble(record.get(8)));

                if (shp.isDefined()) {
                    Geometry serviceArea = shp.getGeometry();
                    if (!MGC.coord2Point(fromCoord).within(serviceArea) || !MGC.coord2Point(toCoord).within(serviceArea)) {
                        continue;
                    }
                }

                Link fromLink = NetworkUtils.getNearestLink(network, fromCoord);
                Link toLink = NetworkUtils.getNearestLink(network, toCoord);
                Activity fromAct = populationFactory.createActivityFromLinkId("dummy", fromLink.getId());
                fromAct.setEndTime(departureTime);
                Leg leg = populationFactory.createLeg(TransportMode.drt);
                Activity toAct = populationFactory.createActivityFromLinkId("dummy", toLink.getId());
                Plan plan = populationFactory.createPlan();
                plan.addActivity(fromAct);
                plan.addLeg(leg);
                plan.addActivity(toAct);
                Person person = populationFactory.createPerson(Id.createPersonId("drt_person_" + counter));
                person.addPlan(plan);
                outputPopulation.addPerson(person);

                counter++;
            }
        }

        PopulationWriter populationWriter = new PopulationWriter(outputPopulation);
        populationWriter.write(outputPath.toString());

        return 0;
    }
}
