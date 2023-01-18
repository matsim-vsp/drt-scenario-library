package org.matsim.prepare.oranienburg;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.*;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.ShpOptions;
import org.matsim.contrib.dvrp.path.VrpPaths;
import org.matsim.contrib.dvrp.router.TimeAsTravelDisutility;
import org.matsim.contrib.dvrp.trafficmonitoring.QSimFreeSpeedTravelTime;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.router.speedy.SpeedyALTFactory;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.geometry.geotools.MGC;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class ExtractTripsFromOutputTrips implements MATSimAppCommand {
    private static final Logger log = LogManager.getLogger(ExtractTripsFromOutputTrips.class);

    @CommandLine.Option(names = "--input", description = "input trips csv file", required = true)
    private Path input;

    @CommandLine.Option(names = "--output", description = "output plans file", required = true)
    private String output;

    @CommandLine.Option(names = "--network", description = "input network files", required = true)
    private String networkPath;

    @CommandLine.Option(names = "--timetable", description = "output plans file", required = true)
    private Path timetable;

    @CommandLine.Option(names = "--alpha", description = "DRT max travel time alpha", defaultValue = "2.0")
    private double alpha;

    @CommandLine.Option(names = "--beta", description = "DRT max travel time beta", defaultValue = "900")
    private double beta;

    @CommandLine.Mixin
    private ShpOptions shp = new ShpOptions();

    enum Type {ARRIVAL_TIMETABLE, DEPARTURE_TIMETABLE}

    public static void main(String[] args) {
        new ExtractTripsFromOutputTrips().execute(args);
    }

    @Override
    public Integer call() throws Exception {
        Random random = new Random(1234);
        Geometry studyArea = shp.getGeometry();
        Id<Link> trainStationLinkId = Id.createLinkId("5405906940001r");

        Population outputPlans = ScenarioUtils.createScenario(ConfigUtils.createConfig()).getPopulation();
        PopulationFactory populationFactory = outputPlans.getFactory();

        Map<String, List<Double>> trainTimetable = readTimetable();

        Network network = NetworkUtils.readNetwork(networkPath);
        TravelTime travelTime = new QSimFreeSpeedTravelTime(1);
        LeastCostPathCalculator router = new SpeedyALTFactory().createPathCalculator(network, new TimeAsTravelDisutility(travelTime), travelTime);

        // We should map the departure location to slow roads (i.e. excluding motorways, highways...)
        List<Link> possibleLinks = new ArrayList<>();
        for (Link link : network.getLinks().values()) {
            if (link.getFreespeed() < 8.4) { // This speed also guarantees the link is within the populated area
                possibleLinks.add(link);
            }
        }

        // Start Preparing DRT plans
        int drtTripCounter = 0;
        try (
                CSVParser parser = new CSVParser(Files.newBufferedReader(input),
                        CSVFormat.DEFAULT.withDelimiter(';').withFirstRecordAsHeader())) {
            for (CSVRecord record : parser.getRecords()) {
                String mode = record.get("main_mode");
                if (mode.equals(TransportMode.pt)) {
                    Coord fromCoord = new Coord(Double.parseDouble(record.get("start_x")), Double.parseDouble(record.get("start_y")));
                    Coord toCoord = new Coord(Double.parseDouble(record.get("end_x")), Double.parseDouble(record.get("end_y")));
                    Point from = MGC.coord2Point(fromCoord);
                    Point to = MGC.coord2Point(toCoord);
                    String[] hmsDeparture = record.get("dep_time").split(":");
                    int hour = Integer.parseInt(hmsDeparture[0]) % 24;
                    double departureTime = hour * 3600 + Integer.parseInt(hmsDeparture[1]) * 60 + Integer.parseInt(hmsDeparture[2]);

                    String[] hmsTravelTime = record.get("trav_time").split(":");
                    int hourTravelTime = Integer.parseInt(hmsTravelTime[0]) % 24;
                    double journeyTime = hourTravelTime * 3600 + Integer.parseInt(hmsTravelTime[1]) * 60 + Integer.parseInt(hmsTravelTime[2]);
                    double arrivalTime = departureTime + journeyTime;

                    if (from.within(studyArea) || to.within(studyArea)) {
                        Person drtPerson = populationFactory.createPerson(Id.createPersonId("drt_passenger_" + drtTripCounter));
                        Plan plan = populationFactory.createPlan();
                        Activity fromAct;
                        Activity toAct;
                        Leg leg = populationFactory.createLeg(TransportMode.drt);

                        if (from.within(studyArea) && to.within(studyArea)) { // pt trips within the area. Keep the locations and departure time unchanged
                            fromAct = populationFactory.createActivityFromLinkId("dummy", getNearestLink(fromCoord, possibleLinks).getId());
                            fromAct.setEndTime(departureTime);
                            toAct = populationFactory.createActivityFromLinkId("dummy", getNearestLink(toCoord, possibleLinks).getId());
                        } else if (from.within(studyArea)) { // trips traveling to train station
                            Link fromLink = getNearestLink(fromCoord, possibleLinks);
                            Link toLink = network.getLinks().get(trainStationLinkId);
                            if (CoordUtils.calcEuclideanDistance(fromLink.getToNode().getCoord(), toLink.getToNode().getCoord()) < 1000) { // Reassign a new link for trips that are too short
                                fromLink = possibleLinks.get(random.nextInt(possibleLinks.size() - 1));
                            }
                            Link fromLinkForRouteCalc = network.getLinks().get(fromLink.getId()); // For route calculation, the link from the original network should be used! Otherwise, it may have some problem
                            double directTravelTime = VrpPaths.calcAndCreatePath(fromLinkForRouteCalc, toLink, departureTime, router, travelTime).getTravelTime();
                            double allocatedTravelTime = Math.floor(alpha * directTravelTime + beta);
                            double arrivalTimeAtStation = departureTime + allocatedTravelTime;
                            double updatedArrivalTime = adaptToTimetable(trainTimetable, arrivalTimeAtStation, random.nextDouble(), Type.DEPARTURE_TIMETABLE);
                            double updatedDepartureTime = updatedArrivalTime - allocatedTravelTime;

                            fromAct = populationFactory.createActivityFromLinkId("dummy", fromLink.getId());
                            fromAct.setEndTime(updatedDepartureTime);
                            toAct = populationFactory.createActivityFromLinkId("dummy", trainStationLinkId);
                        } else { // trips starting from train station
                            Link fromLink = network.getLinks().get(trainStationLinkId);
                            Link toLink = getNearestLink(toCoord, possibleLinks);
                            if (CoordUtils.calcEuclideanDistance(fromLink.getToNode().getCoord(), toLink.getToNode().getCoord()) < 1000) { // Reassign a new link for trips that are too short
                                toLink = possibleLinks.get(random.nextInt(possibleLinks.size() - 1));
                            }
                            Link toLinkForRouteCalc = network.getLinks().get(toLink.getId()); // For route calculation, the link from the original network should be used! Otherwise, it may have some problem
                            double directTravelTime = VrpPaths.calcAndCreatePath(fromLink, toLinkForRouteCalc, departureTime, router, travelTime).getTravelTime(); // If the network is time-varying, then this value may be not accurate
                            double allocatedTravelTime = Math.floor(alpha * directTravelTime + beta);
                            double originalDepartureTimeFromStation = arrivalTime - allocatedTravelTime;
                            double updatedDepartureTimeFromStation = adaptToTimetable(trainTimetable, originalDepartureTimeFromStation, random.nextDouble(), Type.ARRIVAL_TIMETABLE);

                            fromAct = populationFactory.createActivityFromLinkId("dummy", trainStationLinkId);
                            fromAct.setEndTime(updatedDepartureTimeFromStation);
                            toAct = populationFactory.createActivityFromLinkId("dummy", toLink.getId());
                        }
                        // Create plan and add plan to the person. Finally, add person to output population file
                        plan.addActivity(fromAct);
                        plan.addLeg(leg);
                        plan.addActivity(toAct);
                        drtPerson.addPlan(plan);
                        outputPlans.addPerson(drtPerson);
                        drtTripCounter++;
                    }
                }
            }

            PopulationWriter populationWriter = new PopulationWriter(outputPlans);
            populationWriter.write(output);
            return 0;
        }
    }

    private double adaptToTimetable(Map<String, List<Double>> trainTimetable, double originalTime, double randomNumber, Type type) {
        List<Double> timeList;
        if (randomNumber < 0.5) {
            // Take RE 5
            if (type == Type.DEPARTURE_TIMETABLE) {
                timeList = trainTimetable.get("RE5_departure");
            } else {
                timeList = trainTimetable.get("RE5_arrival");
            }
        } else {
            // Take other trains (S-Bahn, RB 32)
            if (type == Type.DEPARTURE_TIMETABLE) {
                timeList = trainTimetable.get("others_departure");
            } else {
                timeList = trainTimetable.get("others_arrival");
            }
        }
        return getNearestTime(originalTime, timeList);
    }

    private double getNearestTime(double originalTime, List<Double> timeList) {
        double minAbsDifference = Double.MAX_VALUE;
        double updatedTime = 0;
        for (double time : timeList) {
            double absDifference = Math.abs(originalTime - time);
            if (absDifference < minAbsDifference) {
                minAbsDifference = absDifference;
                updatedTime = time;
            }
        }
        return updatedTime;
    }

    private Map<String, List<Double>> readTimetable() throws IOException {
        Map<String, List<Double>> trainTimetableMap = new HashMap<>();
        try (
                CSVParser parser = new CSVParser(Files.newBufferedReader(timetable), CSVFormat.DEFAULT.withFirstRecordAsHeader())) {
            for (CSVRecord record : parser.getRecords()) {
                String stopType = record.get("type");
                String route = record.get(1);
                double time = Double.parseDouble(record.get(2));
                if (route.equals("RE5")) {
                    if (stopType.equals("departure")) {
                        trainTimetableMap.computeIfAbsent("RE5_departure", t -> new ArrayList<>()).add(time);
                    } else {
                        trainTimetableMap.computeIfAbsent("RE5_arrival", t -> new ArrayList<>()).add(time);
                    }
                } else {
                    if (stopType.equals("departure")) {
                        trainTimetableMap.computeIfAbsent("others_departure", t -> new ArrayList<>()).add(time);
                    } else {
                        trainTimetableMap.computeIfAbsent("others_arrival", t -> new ArrayList<>()).add(time);
                    }
                }
            }
        }
        return trainTimetableMap;
    }

    private Link getNearestLink(Coord coord, List<Link> possibleLinks) {
        double minDistance = Double.MAX_VALUE;
        Link nearestLink = null;
        for (Link link : possibleLinks) {
            double distance = CoordUtils.calcEuclideanDistance(coord, link.getToNode().getCoord());
            if (distance < minDistance) {
                minDistance = distance;
                nearestLink = link;
            }
        }
        return nearestLink;
    }

}
