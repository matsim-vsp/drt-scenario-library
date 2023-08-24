package org.matsim.source;

import org.locationtech.jts.geom.Geometry;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.*;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.analysis.DefaultAnalysisMainModeIdentifier;
import org.matsim.application.options.CrsOptions;
import org.matsim.application.options.ShpOptions;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.router.MainModeIdentifier;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.geotools.MGC;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.*;

@CommandLine.Command(
        name = "generate-plans-simple",
        description = "Generate drt plans from population file of the open scenario"
)
public class SimpleDrtRequestGenerator implements MATSimAppCommand {
    @CommandLine.Option(names = "--plans", description = "path to input plans file", required = true)
    private Path inputPlansPath;

    @CommandLine.Option(names = "--network", description = "path to original network file", required = true)
    private Path networkPath;

    @CommandLine.Option(names = "--output-network", description = "path to drt network", defaultValue = "")
    private String outputNetworkPath;

    @CommandLine.Option(names = "--output", description = "output path", required = true)
    private Path outputPath;

    @CommandLine.Option(names = "--conversion-modes", description = "modes of original trips to be converted to DRT, separate with ,", defaultValue = "car,pt")
    private String modes;

    @CommandLine.Option(names = "--conversion-rate", description = "percentage of car trips to be converted to DRT trips, separate with ,", defaultValue = "0.25,1.0")
    private String conversionRatesInput;

    @CommandLine.Option(names = "--start-time", description = "start time to take the request (in second)", defaultValue = "0")
    private double startingTime;

    @CommandLine.Option(names = "--end-time", description = "end time to take the request (in second)", defaultValue = "86400")
    private double endingTime;

    @CommandLine.Mixin
    private ShpOptions shp = new ShpOptions();

    @CommandLine.Mixin
    private CrsOptions crs = new CrsOptions();

    private final Random random = new Random(1234);

    public static void main(String[] args) {
        new SimpleDrtRequestGenerator().execute(args);
    }

    @Override
    public Integer call() throws Exception {
        String[] modesToConvert = modes.split(",");
        String[] conversionRates = conversionRatesInput.split(",");
        if (modesToConvert.length != conversionRates.length) {
            throw new RuntimeException("The length of modes and conversation rate are not the same!");
        }

        Map<String, Double> modeConversionMap = new HashMap<>();
        for (int i = 0; i < modesToConvert.length; i++) {
            modeConversionMap.put(modesToConvert[i], Double.parseDouble(conversionRates[i]));
        }

        Population inputPlans = PopulationUtils.readPopulation(inputPlansPath.toString());
        Network network = NetworkUtils.readNetwork(networkPath.toString());
        Network outputNetwork;
        if (outputNetworkPath.equals("")) {
            outputNetwork = network;
        } else {
            outputNetwork = NetworkUtils.readNetwork(outputNetworkPath);
        }
        PopulationFactory populationFactory = inputPlans.getFactory();
        Population outputPopulation = PopulationUtils.createPopulation(ConfigUtils.createConfig());

        // We don't want the request to start on very long links
        List<Link> linksToRemove = new ArrayList<>();
        for (Link link : outputNetwork.getLinks().values()) {
            if (link.getLength() >= 1000) {
                linksToRemove.add(link);
            }

            if (!link.getAllowedModes().contains(TransportMode.car)) {
                linksToRemove.add(link);
            }
        }
        linksToRemove.forEach(link -> outputNetwork.removeLink(link.getId()));

        List<Node> nodesToRemove = new ArrayList<>();
        for (Node node : outputNetwork.getNodes().values()) {
            if (node.getOutLinks().isEmpty() && node.getOutLinks().isEmpty()) {
                nodesToRemove.add(node);
            }
        }
        nodesToRemove.forEach(node -> outputNetwork.removeNode(node.getId()));

        MainModeIdentifier mainModeIdentifier = new DefaultAnalysisMainModeIdentifier();
        int counter = 0;
        for (Person person : inputPlans.getPersons().values()) {
            List<TripStructureUtils.Trip> trips = TripStructureUtils.getTrips(person.getSelectedPlan());
            for (TripStructureUtils.Trip trip : trips) {
                String mode = mainModeIdentifier.identifyMainMode(trip.getTripElements());
                if (modeConversionMap.containsKey(mode)) {
                    if (random.nextDouble() > modeConversionMap.get(mode)) {
                        continue;
                    }

                    if (trip.getOriginActivity().getEndTime().orElse(-1) < startingTime ||
                            trip.getOriginActivity().getEndTime().orElse(-1) > endingTime) {
                        continue;
                    }

                    Coord fromCoord;
                    Coord toCoord;
                    if (trip.getOriginActivity().getCoord() != null) {
                        fromCoord = trip.getOriginActivity().getCoord();
                    } else {
                        if (network.getLinks().get(trip.getOriginActivity().getLinkId()) == null) {
                            continue;
                        }
                        fromCoord = network.getLinks().get(trip.getOriginActivity().getLinkId()).getToNode().getCoord();
                    }
                    if (trip.getDestinationActivity().getCoord() != null) {
                        toCoord = trip.getDestinationActivity().getCoord();
                    } else {
                        if (network.getLinks().get(trip.getDestinationActivity().getLinkId()) == null) {
                            continue;
                        }
                        toCoord = network.getLinks().get(trip.getDestinationActivity().getLinkId()).getToNode().getCoord();
                    }

                    if (shp.isDefined()) {
                        Geometry serviceArea = shp.getGeometry();
                        if (!MGC.coord2Point(fromCoord).within(serviceArea) || !MGC.coord2Point(toCoord).within(serviceArea)) {
                            continue;
                        }
                    }


                    if (crs != null) {
                        CoordinateTransformation cf = crs.getTransformation();
                        fromCoord = cf.transform(fromCoord);
                        toCoord = cf.transform(toCoord);
                    }

                    // Now,  we create a drt person based on this trip
                    // Then we create the plans
                    Person dummyPerson = populationFactory.createPerson(Id.createPersonId("drt_person_" + counter));
                    Plan plan = populationFactory.createPlan();
                    Activity fromAct = populationFactory.createActivityFromCoord("dummy", fromCoord);
                    fromAct.setEndTime(trip.getOriginActivity().getEndTime().orElse(-1));
                    fromAct.setLinkId(NetworkUtils.getNearestLink(outputNetwork, fromCoord).getId());
                    fromAct.setCoord(null); // This may be not necessary
                    Leg leg = populationFactory.createLeg(TransportMode.drt);
                    Activity toAct = populationFactory.createActivityFromCoord("dummy", toCoord);
                    toAct.setLinkId(NetworkUtils.getNearestLink(outputNetwork, toCoord).getId());
                    toAct.setCoord(null); // This may be not necessary

                    plan.addActivity(fromAct);
                    plan.addLeg(leg);
                    plan.addActivity(toAct);
                    dummyPerson.addPlan(plan);
                    outputPopulation.addPerson(dummyPerson);

                    counter++;
                }
            }
        }

        System.out.println("There are " + counter + " drt trips.");

        PopulationWriter populationWriter = new PopulationWriter(outputPopulation);
        populationWriter.write(outputPath.toString());
        return 0;
    }
}
