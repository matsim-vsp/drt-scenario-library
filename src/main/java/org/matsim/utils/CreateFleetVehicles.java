/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2016 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.utils;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.locationtech.jts.geom.Geometry;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.ShpOptions;
import org.matsim.contrib.dvrp.fleet.DvrpVehicle;
import org.matsim.contrib.dvrp.fleet.DvrpVehicleSpecification;
import org.matsim.contrib.dvrp.fleet.FleetWriter;
import org.matsim.contrib.dvrp.fleet.ImmutableDvrpVehicleSpecification;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.utils.geometry.geotools.MGC;
import picocli.CommandLine;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * @author jbischoff, modified by luchengqi7
 * This is an example script to create a vehicle file for taxis, SAV or DRTs.
 * The vehicles are distributed randomly in the network.
 */

@CommandLine.Command(
        name = "create-fleet",
        description = "create drt fleet"
)
public class CreateFleetVehicles implements MATSimAppCommand {
    @CommandLine.Option(names = "--network", description = "path to network file", required = true)
    private Path networkFile;

    @CommandLine.Option(names = "--fleet-size-from", description = "number of vehicles to generate", required = true)
    private int fleetSizeFrom;

    @CommandLine.Option(names = "--fleet-size-to", description = "number of vehicles to generate", required = true)
    private int fleetSizeTo;

    @CommandLine.Option(names = "--fleet-size-interval", description = "number of vehicles to generate", defaultValue = "10")
    private int fleetSizeInterval;

    @CommandLine.Option(names = "--capacity", description = "capacity of the vehicle", required = true)
    private int capacity;

    @CommandLine.Option(names = "--output-folder", description = "path to output folder", required = true)
    private Path outputFolder;

    @CommandLine.Option(names = "--operator", description = "name of the operator", defaultValue = "drt")
    private String operator;

    @CommandLine.Option(names = "--start-time", description = "service starting time", defaultValue = "0")
    private double startTime;

    @CommandLine.Option(names = "--end-time", description = "service ending time", defaultValue = "86400")
    private double endTime;

    @CommandLine.Option(names = "--depots", description = "Path to the depots location file", defaultValue = "")
    private String depotsPath;

    @CommandLine.Mixin
    private ShpOptions shp = new ShpOptions(); // Optional input for service area (shape file)

    private static final Random random = MatsimRandom.getRandom();


    public static void main(String[] args) {
        new CreateFleetVehicles().execute(args);
    }

    @Override
    public Integer call() throws Exception {
        if (!Files.exists(outputFolder)) {
            Files.createDirectory(outputFolder);
        }

        Network network = NetworkUtils.readNetwork(networkFile.toString());
        List<Link> links = network.getLinks().values().stream().
                filter(l -> l.getAllowedModes().contains(TransportMode.car)).
                collect(Collectors.toList());
        if (shp.isDefined()) {
            Geometry serviceArea = shp.getGeometry();
            links = links.stream().
                    filter(l -> MGC.coord2Point(l.getToNode().getCoord()).within(serviceArea)).
                    collect(Collectors.toList());
        }

        if (!depotsPath.equals("")) {
            try (CSVParser parser = new CSVParser(Files.newBufferedReader(Path.of(depotsPath), StandardCharsets.UTF_8),
                    CSVFormat.DEFAULT.withDelimiter(',').withFirstRecordAsHeader())) {
                links.clear();
                for (CSVRecord record : parser) {
                    Link depotLink = network.getLinks().get(Id.createLinkId(record.get(0)));
                    links.add(depotLink);
                }
            }
        }

        for (int fleetSize = fleetSizeFrom; fleetSize <= fleetSizeTo; fleetSize += fleetSizeInterval) {
            System.out.println("Creating fleet: " + fleetSize);
            List<DvrpVehicleSpecification> vehicleSpecifications = new ArrayList<>();
            for (int i = 0; i < fleetSize; i++) {
                Id<Link> startLinkId;
                if (depotsPath.equals("")) {
                    startLinkId = links.get(random.nextInt(links.size())).getId();
                } else {
                    startLinkId = links.get(i % links.size()).getId(); // Even distribution of the vehcles
                }

                DvrpVehicleSpecification vehicleSpecification = ImmutableDvrpVehicleSpecification.newBuilder().
                        id(Id.create(operator + "_" + i, DvrpVehicle.class)).
                        startLinkId(startLinkId).
                        capacity(capacity).
                        serviceBeginTime(startTime).
                        serviceEndTime(endTime).build();
                vehicleSpecifications.add(vehicleSpecification);
            }
            new FleetWriter(vehicleSpecifications.stream()).write(outputFolder.toString() + "/" + fleetSize + "-" + capacity + "_seater-" + operator + "-vehicles.xml");
        }
        return 0;
    }
}
