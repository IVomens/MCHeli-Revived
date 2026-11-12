package com.ivomens.mcheli.aircraft;

/** Entities that can be loaded onto an aircraft's rack. */
public interface IRideableAboard {

    boolean isSkipNormalRender();

    boolean canRideAircraft(AircraftEntity aircraft, int seatId, SeatRackInfo rackInfo);
}
