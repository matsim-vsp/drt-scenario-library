package org.matsim.utils;

import org.matsim.contrib.drt.optimizer.insertion.IncrementalStopDurationEstimator;
import org.matsim.contrib.drt.passenger.DrtRequest;
import org.matsim.contrib.drt.schedule.DrtStopTask;
import org.matsim.contrib.dvrp.fleet.DvrpVehicle;

public record LinearDrtStopDurationEstimator(double fixedStopDuration) implements IncrementalStopDurationEstimator {

    @Override
    public double calcForPickup(DvrpVehicle vehicle, DrtStopTask stopTask, DrtRequest pickupRequest) {
        return fixedStopDuration;
    }

    @Override
    public double calcForDropoff(DvrpVehicle vehicle, DrtStopTask stopTask, DrtRequest dropoffRequest) {
        return fixedStopDuration;
    }
}
