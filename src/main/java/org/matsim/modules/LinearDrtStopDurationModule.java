package org.matsim.modules;

import org.matsim.contrib.drt.optimizer.insertion.IncrementalStopDurationEstimator;
import org.matsim.contrib.drt.passenger.DrtRequest;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.schedule.DrtStopTask;
import org.matsim.contrib.drt.schedule.StopDurationEstimator;
import org.matsim.contrib.dvrp.fleet.DvrpVehicle;
import org.matsim.contrib.dvrp.run.AbstractDvrpModeModule;

public class LinearDrtStopDurationModule extends AbstractDvrpModeModule {
    private final DrtConfigGroup drtCfg;

    public LinearDrtStopDurationModule(DrtConfigGroup drtCfg) {
        super(drtCfg.mode);
        this.drtCfg = drtCfg;
    }

    @Override
    public void install() {
        bindModal(StopDurationEstimator.class).toInstance((vehicle, dropoffRequests, pickupRequests) -> drtCfg.stopDuration * (dropoffRequests.size() + pickupRequests.size()));
        bindModal(IncrementalStopDurationEstimator.class).toInstance(new LinearDrtStopDurationEstimator(drtCfg.stopDuration));
    }

    record LinearDrtStopDurationEstimator(double fixedStopDuration) implements IncrementalStopDurationEstimator {

        @Override
        public double calcForPickup(DvrpVehicle vehicle, DrtStopTask stopTask, DrtRequest pickupRequest) {
            return fixedStopDuration;
        }

        @Override
        public double calcForDropoff(DvrpVehicle vehicle, DrtStopTask stopTask, DrtRequest dropoffRequest) {
            return fixedStopDuration;
        }
    }
}
