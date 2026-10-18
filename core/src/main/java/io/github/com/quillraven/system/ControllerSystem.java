package io.github.com.quillraven.system;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import io.github.com.quillraven.component.Controller;
import io.github.com.quillraven.component.Facing;
import io.github.com.quillraven.component.Move;
import io.github.com.quillraven.component.Player;
import io.github.com.quillraven.input.Command;

import java.util.EnumSet;

public final class ControllerSystem extends IteratingSystem {
    private final ChessPieceSystem chessPieceSystem;
    private final ChessDuelSystem chessDuelSystem;
    private final Runnable onAcceptChallenge;
    private final EnumSet<Command> heldMovement = EnumSet.noneOf(Command.class);
    private boolean pawnArena;
    private boolean chessDuel;

    public ControllerSystem(ChessPieceSystem chessPieceSystem, ChessDuelSystem chessDuelSystem,
                            Runnable onAcceptChallenge) {
        super(Family.all(Controller.class).get());
        this.chessPieceSystem = chessPieceSystem;
        this.chessDuelSystem = chessDuelSystem;
        this.onAcceptChallenge = onAcceptChallenge;
    }

    public void setPawnArena(boolean pawnArena) {
        this.pawnArena = pawnArena;
        setChessDuel(false);
        clearControllerInput();
    }

    public void setChessDuel(boolean chessDuel) {
        this.chessDuel = chessDuel;
        heldMovement.clear();
        clearControllerInput();
    }

    private void clearControllerInput() {
        for (Entity entity : getEntities()) {
            Controller controller = Controller.MAPPER.get(entity);
            controller.getPressedCommands().clear();
            controller.getReleasedCommands().clear();
            Move move = Move.MAPPER.get(entity);
            if (move != null) move.getDirection().setZero();
        }
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        Controller controller = Controller.MAPPER.get(entity);
        if (chessDuel && Player.MAPPER.get(entity) != null) {
            for (Command command : controller.getPressedCommands()) chessDuelSystem.handleCommand(command);
            controller.getPressedCommands().clear();
            controller.getReleasedCommands().clear();
            return;
        }
        if (pawnArena && Player.MAPPER.get(entity) != null) {
            for (Command command : controller.getPressedCommands()) {
                if (command != Command.INTERACT) {
                    chessPieceSystem.movePlayer(command);
                    break;
                }
            }
            controller.getPressedCommands().clear();
            controller.getReleasedCommands().clear();
            return;
        }

        boolean acceptChallenge = false;
        for (Command command : controller.getPressedCommands()) {
            switch (command) {
                case UP, DOWN, LEFT, RIGHT -> heldMovement.add(command);
                case INTERACT, SELECT -> acceptChallenge = true;
                case CANCEL, CAPTURE_LEFT, CAPTURE_RIGHT -> {
                }
            }
        }
        for (Command command : controller.getReleasedCommands()) heldMovement.remove(command);
        controller.getPressedCommands().clear();
        controller.getReleasedCommands().clear();

        if (Player.MAPPER.get(entity) == null) return;

        Move move = Move.MAPPER.get(entity);
        if (move == null) return;
        float directionX = (heldMovement.contains(Command.RIGHT) ? 1f : 0f)
            - (heldMovement.contains(Command.LEFT) ? 1f : 0f);
        float directionY = (heldMovement.contains(Command.UP) ? 1f : 0f)
            - (heldMovement.contains(Command.DOWN) ? 1f : 0f);
        move.getDirection().set(directionX, directionY);
        Facing facing = Facing.MAPPER.get(entity);
        if (facing != null && (directionX != 0f || directionY != 0f)) {
            if (Math.abs(directionX) > Math.abs(directionY)) {
                facing.setDirection(directionX > 0f ? Facing.FacingDirection.RIGHT : Facing.FacingDirection.LEFT);
            } else {
                facing.setDirection(directionY > 0f ? Facing.FacingDirection.UP : Facing.FacingDirection.DOWN);
            }
        }
        if (acceptChallenge) onAcceptChallenge.run();
    }
}
