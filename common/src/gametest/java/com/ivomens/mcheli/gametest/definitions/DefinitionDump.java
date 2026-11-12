package com.ivomens.mcheli.gametest.definitions;

import com.ivomens.mcheli.vehicle.definition.PartDef;
import com.ivomens.mcheli.vehicle.definition.VehicleDefinition;

import java.util.List;

/**
 * Writes vehicle definitions as canonical text, one value per line, so a diff of the golden file
 * points at the field that changed. Numbers print with {@code Float/Double.toString}, which
 * round-trips exactly.
 */
final class DefinitionDump {

    private DefinitionDump() {
    }

    static void write(VehicleDefinition definition, List<String> out) {
        out.add(definition.kind().directory() + "/" + definition.name());
        out.add("  handling " + definition.handling());
        out.add("  body " + definition.body());
        out.add("  wingSweep " + definition.wingSweep());
        out.add("  targetDrone " + definition.targetDrone());
        List<?> seats = definition.seats();
        for (int i = 0; i < seats.size(); i++) {
            out.add("  seat " + i + " " + seats.get(i));
        }
        List<?> mounts = definition.mounts();
        for (int i = 0; i < mounts.size(); i++) {
            out.add("  mount " + i + " " + mounts.get(i));
        }
        writeParts(definition.parts(), "  ", out);
    }

    private static void writeParts(List<PartDef> parts, String indent, List<String> out) {
        for (PartDef part : parts) {
            out.add(indent + "part " + part.kind() + " " + part.modelName() + " pos=" + part.pos() + " rot="
                    + part.rot());
            writeParts(part.children(), indent + "  ", out);
        }
    }
}
