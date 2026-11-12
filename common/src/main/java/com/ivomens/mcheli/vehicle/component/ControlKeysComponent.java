package com.ivomens.mcheli.vehicle.component;

/**
 * The movement keys the pilot holds: throttle up and down, left and right. The pilot's client sets
 * them from the keyboard and sends them; the server publishes them in the status word so the other
 * clients can animate wheels, tracks and engine sound with them.
 * <p>
 * The {@code hold...} setters return the new state, so the client can set a key and put it into the
 * control packet in one go.
 */
public final class ControlKeysComponent implements VehicleComponent {

    private final StatusWord status;
    private boolean brakeFitted;
    private boolean throttleUp;
    private boolean throttleDown;
    private boolean moveLeft;
    private boolean moveRight;

    public ControlKeysComponent(StatusWord status) {
        this.status = status;
    }

    /** Only vehicles with a brake (tanks) have the brake key. */
    public void fitBrake() {
        this.brakeFitted = true;
    }

    public boolean hasBrake() {
        return this.brakeFitted;
    }

    public boolean brake() {
        return this.status.get(StatusWord.BRAKE);
    }

    /** Server: the driver holds or releases the brake. */
    public void setBrake(boolean brake) {
        this.status.set(StatusWord.BRAKE, brake);
    }

    public boolean throttleUp() {
        return this.throttleUp;
    }

    public boolean throttleDown() {
        return this.throttleDown;
    }

    public boolean moveLeft() {
        return this.moveLeft;
    }

    public boolean moveRight() {
        return this.moveRight;
    }

    public boolean holdThrottleUp(boolean held) {
        this.throttleUp = held;
        return held;
    }

    public boolean holdThrottleDown(boolean held) {
        this.throttleDown = held;
        return held;
    }

    public boolean holdMoveLeft(boolean held) {
        this.moveLeft = held;
        return held;
    }

    public boolean holdMoveRight(boolean held) {
        this.moveRight = held;
        return held;
    }

    public void releaseAll() {
        this.moveLeft = false;
        this.moveRight = false;
        this.throttleDown = false;
        this.throttleUp = false;
    }

    /** Server: tells every client which keys the pilot holds. */
    public void publish() {
        this.status.set(StatusWord.MOVE_LEFT, this.moveLeft);
        this.status.set(StatusWord.MOVE_RIGHT, this.moveRight);
        this.status.set(StatusWord.THROTTLE_UP, this.throttleUp);
        this.status.set(StatusWord.THROTTLE_DOWN, this.throttleDown);
    }

    /** Client: takes the keys of a vehicle someone else flies from the status word. */
    public void mirror() {
        this.moveLeft = this.status.get(StatusWord.MOVE_LEFT);
        this.moveRight = this.status.get(StatusWord.MOVE_RIGHT);
        this.throttleUp = this.status.get(StatusWord.THROTTLE_UP);
        this.throttleDown = this.status.get(StatusWord.THROTTLE_DOWN);
    }
}
