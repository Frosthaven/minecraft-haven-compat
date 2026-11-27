package dev.frosthaven.havencompat.contraptions;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.elevator.ElevatorContraption;
import com.simibubi.create.content.contraptions.elevator.ElevatorFloorListPacket;
import dev.frosthaven.havencompat.Log;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Server side. A player who starts seeing an elevator cabin gets its floor list at once.
 * <p>
 * Create sends an elevator's floors in two ways only: a game asks for them once, when its copy of the cabin attaches
 * to the pulley ({@code ElevatorPulleyBlockEntity.attach}), and the server sends them to the players tracking the
 * cabin when the list changes ({@code ElevatorContraption.tickStorage}, a new column version). A game that missed the
 * first (the cabin came into view later, a rejoin, a trip through a portal) kept an empty list until a floor changed,
 * and the cabin's Contraption Controls showed "No Floors" while the server had both floors (Shane's elevator at
 * -665, 1660, 2026-11-26, twice; editing a contact brought the list back). Here: on NeoForge's start-tracking event,
 * an elevator cabin with floors sends them to that one player.
 */
public final class ElevatorFloors {
    private static final Log LOG = new Log("[elevator floors] ");
    private static int logged;

    private ElevatorFloors() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(PlayerEvent.StartTracking.class, e -> {
            if (!(e.getEntity() instanceof ServerPlayer player) || !(e.getTarget() instanceof AbstractContraptionEntity cabin)) return;
            if (!(cabin.getContraption() instanceof ElevatorContraption elevator) || elevator.namesList == null || elevator.namesList.isEmpty()) return;
            CatnipServices.NETWORK.sendToClient(player, new ElevatorFloorListPacket(cabin, elevator.namesList));
            if (logged++ < 20) LOG.info("sent the {} floors of the elevator at {} to {}, who just started seeing it", elevator.namesList.size(),
                    cabin.blockPosition().toShortString(), player.getName().getString());
        });
    }
}
