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
import org.matsim.api.core.v01.population.*;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.ShpOptions;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.geotools.MGC;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;

public class ExtractTripsFromOutputTrips implements MATSimAppCommand {
    private static final Logger log = LogManager.getLogger(ExtractTripsFromOutputTrips.class);

    @CommandLine.Option(names = "--input", description = "input trips csv file", required = true)
    private Path input;

    @CommandLine.Option(names = "--output", description = "output plans file", required = true)
    private String output;

    @CommandLine.Option(names = "--network", description = "input network files", required = true)
    private String network;

    @CommandLine.Option(names = "--timetable", description = "output plans file", required = true)
    private String timetable;

    @CommandLine.Option(names = "--alpha", description = "DRT max travel time alpha", defaultValue = "2.0")
    private double alpha;

    @CommandLine.Option(names = "--beta", description = "DRT max travel time beta", defaultValue = "900")
    private double beta;

    @CommandLine.Mixin
    private ShpOptions shp = new ShpOptions();

    public static void main(String[] args) {
        new ExtractTripsFromOutputTrips().execute(args);
    }

    @Override
    public Integer call() throws Exception {
        Geometry studyArea = shp.getGeometry();
        Id<Link> trainStationLinkId = Id.createLinkId("5405906940001r");

        Population outputPlans = ScenarioUtils.createScenario(ConfigUtils.createConfig()).getPopulation();
        PopulationFactory populationFactory = outputPlans.getFactory();
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
                    double travelTime = hourTravelTime * 3600 + Integer.parseInt(hmsTravelTime[1]) * 60 + Integer.parseInt(hmsTravelTime[2]);
                    double arrivalTime = departureTime + travelTime;


                    if (from.within(studyArea) || to.within(studyArea)) {
                        Person drtPerson = populationFactory.createPerson(Id.createPersonId("drt_passenger_" + drtTripCounter));
                        Plan plan = populationFactory.createPlan();
                        Activity fromAct;
                        Activity toAct;
                        Leg leg = populationFactory.createLeg(TransportMode.drt);
                        if (from.within(studyArea) && to.within(studyArea)){
                            fromAct = populationFactory.createActivityFromCoord("dummy", fromCoord);
                            fromAct.setEndTime(departureTime);
                        }


                        if (from.within(studyArea)) {
                            fromAct = populationFactory.createActivityFromCoord("dummy", fromCoord);
                            fromAct.setEndTime(departureTime);
                        } else {
                            fromAct = populationFactory.createActivityFromLinkId("dummy", trainStationLinkId);
                            fromAct.setEndTime(arrivalTime);
                        }
                        if (to.within(studyArea)) {
                            toAct = populationFactory.createActivityFromCoord("dummy", toCoord);
                        } else {
                            toAct = populationFactory.createActivityFromLinkId("dummy", trainStationLinkId);
                        }
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
}
