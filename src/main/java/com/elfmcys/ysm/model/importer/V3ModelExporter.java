package com.elfmcys.ysm.model.importer;

import com.elfmcys.yesstevemodel.resource.pojo.RawYsmModel;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Converts a decrypted v3 {@code .ysm} payload into the classic folder layout that
 * Yes Steve Model 2.x / 3.0 can load.
 */
public final class V3ModelExporter {
    private static final double ARM_SHIFT = Double.parseDouble(System.getProperty("ysm.v3.armShift", "1.6"));

    /**
     * 手臂与袖子同属一组：只移动 Arm 骨骼会让袖子和手臂分家。
     * 这里按骨骼名把手臂/外袖/内衬方块一起处理。
     */
    private static boolean isArmSleeveBone(String boneName) {
        if (boneName == null) {
            return false;
        }
        if (boneName.contains("Arm")) {
            return false;
        }
        return switch (boneName) {
            case "wtR", "wtR2", "bone7", "bone8", "bone9", "bone10" -> true;
            default -> false;
        };
    }

    private static final GsonPretty GSON = new GsonPretty();
    /** v3 容器里每个方块 6 个面的固定顺序。 */
    private static final String[] FACE_NAMES = {"west", "east", "north", "south", "up", "down"};
    private static int rotatedCubes = 0;
    private static int skippedFaces = 0;
    private static int totalCubes = 0;

    /** Writes a decrypted v3 payload into the classic folder layout. */
    public static void export(RawYsmModel model, Path out) throws IOException {
        totalCubes = 0;
        rotatedCubes = 0;
        skippedFaces = 0;
        Files.createDirectories(out);
        writeGeometryAndTextures(model, out);
        writeAnimations(model, out);
        writeControllers(model, out);
        writeData(model.soundFiles, out.resolve("sounds"), ".ogg");
        writeData(model.functionFiles, out.resolve("functions"), ".molang");
        writeLanguages(model.languageFiles, out.resolve("lang"));
        writeMetadata(model, out);
        com.elfmcys.ysm.YesSteveModel.LOGGER.info(
                "v3 export: cubes={} rotated={} facesWithoutAxis={}",
                totalCubes, rotatedCubes, skippedFaces);
    }

    // ── geometry + textures ────────────────────────────────────────────────

    private static void writeGeometryAndTextures(RawYsmModel model, Path out) throws IOException {
        RawYsmModel.RawMainEntity entity = model.mainEntity;
        if (entity == null) {
            return;
        }
        Path models = Files.createDirectories(out.resolve("models"));
        Path textures = Files.createDirectories(out.resolve("textures"));

        int texW = 64;
        int texH = 64;
        if (entity.textures != null) {
            for (RawYsmModel.RawTexture tex : entity.textures.values()) {
                if (tex != null && tex.width > 0 && tex.height > 0) {
                    texW = tex.width;
                    texH = tex.height;
                    break;
                }
            }
        }
        writeGeometry(entity.mainModel, models.resolve("main.json"), texW, texH);
        writeGeometry(entity.armModel, models.resolve("arm.json"), texW, texH);
        if (entity.textures != null) {
            for (RawYsmModel.RawTexture tex : entity.textures.values()) {
                writeTexture(tex, textures);
            }
        }
        for (Map.Entry<String, RawYsmModel.RawSubEntity> e : model.vehicles.entrySet()) {
            writeSubEntity(e.getKey(), e.getValue(), models, textures);
        }
        for (Map.Entry<String, RawYsmModel.RawSubEntity> e : model.projectiles.entrySet()) {
            writeSubEntity(e.getKey(), e.getValue(), models, textures);
        }
    }

    private static void writeSubEntity(String key, RawYsmModel.RawSubEntity sub, Path models, Path textures) throws IOException {
        if (sub == null) {
            return;
        }
        String safe = key.replace(':', '_');
        if (sub.model != null) {
            writeGeometry(sub.model, models.resolve(safe + ".json"), (int) sub.model.textureWidth, (int) sub.model.textureHeight);
        }
        if (sub.textures != null) {
            for (RawYsmModel.RawTexture tex : sub.textures.values()) {
                writeTexture(tex, textures);
            }
        }
    }

    private static String firstTexturePath(RawYsmModel.RawSubEntity sub, RawYsmModel model) {
        if (sub != null && sub.textures != null) {
            for (RawYsmModel.RawTexture tex : sub.textures.values()) {
                if (tex != null && tex.data != null && tex.data.length > 0) {
                    String name = tex.name == null || tex.name.isEmpty() ? "skin" : tex.name.replace('\\', '_').replace('/', '_');
                    return "textures/" + name + ".png";
                }
            }
        }
        if (model != null && model.mainEntity != null && model.mainEntity.textures != null) {
            for (RawYsmModel.RawTexture tex : model.mainEntity.textures.values()) {
                if (tex != null && tex.data != null && tex.data.length > 0) {
                    String name = tex.name == null || tex.name.isEmpty() ? "skin" : tex.name.replace('\\', '_').replace('/', '_');
                    return "textures/" + name + ".png";
                }
            }
        }
        return null;
    }

    private static void writeGeometry(RawYsmModel.RawGeometry geo, Path file, int texW, int texH) throws IOException {
        if (geo == null) {
            return;
        }
        JsonObject root = new JsonObject();
        root.addProperty("format_version", "1.12.0");
        JsonArray geometries = new JsonArray();
        JsonObject geometry = new JsonObject();

        JsonObject desc = new JsonObject();
        desc.addProperty("identifier", geo.identifier == null || geo.identifier.isEmpty() ? "geometry.unknown" : geo.identifier);
        desc.addProperty("texture_width", texW);
        desc.addProperty("texture_height", texH);
        desc.addProperty("visible_bounds_width", geo.visibleBoundsWidth);
        desc.addProperty("visible_bounds_height", geo.visibleBoundsHeight);
        JsonArray offset = new JsonArray();
        float[] off = geo.visibleBoundsOffset == null ? new float[]{0, 0, 0} : geo.visibleBoundsOffset;
        for (int i = 0; i < 3; i++) {
            offset.add(i < off.length ? off[i] : 0f);
        }
        desc.add("visible_bounds_offset", offset);
        geometry.add("description", desc);

        JsonArray bones = new JsonArray();
        for (RawYsmModel.RawBone bone : geo.bones) {
            boneToJson(bones, bone, texW, texH);
        }
        geometry.add("bones", bones);
        geometries.add(geometry);
        root.add("minecraft:geometry", geometries);
        Files.writeString(file, GSON.toJson(root));
    }

    /**
     * Emits the bone plus one child bone per rotated cube. The rotation lives on the child bone
     * (bone rotation is the loader's primary, well-tested path) with the cube centre as pivot, so
     * the cube itself stays axis aligned in its own bone space.
     */
    private static void boneToJson(JsonArray out, RawYsmModel.RawBone bone, int srcW, int srcH) {
        String boneName = bone.name == null ? "" : bone.name;
        JsonObject node = new JsonObject();
        node.addProperty("name", boneName);
        String effectiveParent = bone.parentName;
        if (isArmSleeveBone(boneName) && bone.cubes != null && !bone.cubes.isEmpty()) {
            // 网格已经镜像过：pivot 为正的一侧是 RightArm
            double side = (bone.pivot != null && bone.pivot.length > 0) ? bone.pivot[0] : 0.0;
            effectiveParent = side > 0 ? "RightArm" : "LeftArm";
        }
        if (effectiveParent != null && !effectiveParent.isEmpty() && !"-".equals(effectiveParent)) {
            node.addProperty("parent", effectiveParent);
        }
        if (bone.pivot != null && bone.pivot.length >= 3) {
            JsonArray pivot = new JsonArray();
            pivot.add(round(bone.pivot[0]));
            pivot.add(round(bone.pivot[1]));
            pivot.add(round(bone.pivot[2]));
            node.add("pivot", pivot);
        } else {
            node.add("pivot", vector(bone.pivot));
        }
        if (bone.rotation != null && (bone.rotation[0] != 0 || bone.rotation[1] != 0 || bone.rotation[2] != 0)) {
            node.add("rotation", vector(bone.rotation));
        }

        JsonArray cubes = new JsonArray();
        JsonArray children = new JsonArray();
        int childIndex = 0;
        if (bone.cubes != null) {
            for (RawYsmModel.RawCube cube : bone.cubes) {
                JsonObject c = cubeToJson(cube, srcW, srcH);
                if (c == null) {
                    continue;
                }
                if (isArmSleeveBone(boneName) && c.has("origin")) {
                    JsonArray origin = c.getAsJsonArray("origin");
                    double ox = origin.get(0).getAsDouble();
                    double shift = ox < 0 ? -ARM_SHIFT : ARM_SHIFT;
                    origin.set(0, new JsonPrimitive(Math.round(ox + shift)));
                }
                double[] meta = null;
                if (meta != null) {
                    JsonObject child = new JsonObject();
                    child.addProperty("name", boneName + "$r" + (childIndex++));
                    child.addProperty("parent", boneName);
                    JsonArray pivot = new JsonArray();
                    pivot.add(round(meta[0]));
                    pivot.add(round(meta[1]));
                    pivot.add(round(meta[2]));
                    child.add("pivot", pivot);
                    JsonArray rotation = new JsonArray();
                    rotation.add(round(meta[3]));
                    rotation.add(round(meta[4]));
                    rotation.add(round(meta[5]));
                    child.add("rotation", rotation);
                    JsonArray childCubes = new JsonArray();
                    childCubes.add(c);
                    child.add("cubes", childCubes);
                    children.add(child);
                } else {
                    cubes.add(c);
                }
            }
        }
        if (!cubes.isEmpty()) {
            node.add("cubes", cubes);
        }
        out.add(node);
        for (var child : children) {
            out.add(child);
        }
    }

    /**
     * Rotation meta for a baked cube: {pivotX, pivotY, pivotZ, rotX, rotY, rotZ, maxSize} in pixels.
     * Returns null when the cube has no usable local basis.
     */
    private static double[] cubeRotationMeta(RawYsmModel.RawCube cube) {
        float[][] rot = localBasis(cube);
        if (rot == null || isIdentity(rot)) {
            return null;
        }
        double sumX = 0, sumY = 0, sumZ = 0;
        int points = 0;
        for (RawYsmModel.RawFace face : cube.faces) {
            for (float[] p : face.positions) {
                sumX += p[0];
                sumY += p[1];
                sumZ += p[2];
                points++;
            }
        }
        if (points == 0) {
            return null;
        }
        double cx = sumX / points, cy = sumY / points, cz = sumZ / points;
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (RawYsmModel.RawFace face : cube.faces) {
            for (float[] p : face.positions) {
                double dx = p[0] - cx, dy = p[1] - cy, dz = p[2] - cz;
                double lx = rot[0][0] * dx + rot[1][0] * dy + rot[2][0] * dz;
                double ly = rot[0][1] * dx + rot[1][1] * dy + rot[2][1] * dz;
                double lz = rot[0][2] * dx + rot[1][2] * dy + rot[2][2] * dz;
                minX = Math.min(minX, lx);
                minY = Math.min(minY, ly);
                minZ = Math.min(minZ, lz);
                maxX = Math.max(maxX, lx);
                maxY = Math.max(maxY, ly);
                maxZ = Math.max(maxZ, lz);
            }
        }
        double maxSize = Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ)) * 16.0;
        return new double[]{
                cx * 16, cy * 16, cz * 16,
                degrees(Math.atan2(rot[2][1], rot[2][2])),
                degrees(Math.asin(clamp(-rot[2][0]))),
                degrees(Math.atan2(rot[1][0], rot[0][0])),
                maxSize
        };
    }

    /**
     * Rebuilds a cube (pixel units) from the baked face data. The baked faces keep the cube's own
     * rotation, so the vertex cloud is projected onto the local axes recovered from the face
     * normals, and the cube is emitted with an explicit rotation/pivot pair.
     */
    private static JsonObject cubeToJson(RawYsmModel.RawCube cube, int srcW, int srcH) {
        if (cube.faces == null || cube.faces.isEmpty()) {
            return null;
        }
        totalCubes++;

        float[][] rot = localBasis(cube);
        if (rot != null && !isIdentity(rot)) {
            rotatedCubes++;
        }

        double sumX = 0, sumY = 0, sumZ = 0;
        int points = 0;
        for (RawYsmModel.RawFace face : cube.faces) {
            for (float[] p : face.positions) {
                sumX += p[0];
                sumY += p[1];
                sumZ += p[2];
                points++;
            }
        }
        if (points == 0) {
            return null;
        }
        double centerX = sumX / points, centerY = sumY / points, centerZ = sumZ / points;

        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (RawYsmModel.RawFace face : cube.faces) {
            for (float[] p : face.positions) {
                double dx = p[0] - centerX, dy = p[1] - centerY, dz = p[2] - centerZ;
                double lx = rot == null ? dx : rot[0][0] * dx + rot[1][0] * dy + rot[2][0] * dz;
                double ly = rot == null ? dy : rot[0][1] * dx + rot[1][1] * dy + rot[2][1] * dz;
                double lz = rot == null ? dz : rot[0][2] * dx + rot[1][2] * dy + rot[2][2] * dz;
                minX = Math.min(minX, lx);
                minY = Math.min(minY, ly);
                minZ = Math.min(minZ, lz);
                maxX = Math.max(maxX, lx);
                maxY = Math.max(maxY, ly);
                maxZ = Math.max(maxZ, lz);
            }
        }

        boolean rotated = rot != null && !isIdentity(rot);

        JsonObject node = new JsonObject();
        JsonArray origin = new JsonArray();
        origin.add(round(-(centerX + maxX) * 16f));
        origin.add(round((centerY + minY) * 16f));
        origin.add(round((centerZ + minZ) * 16f));
        node.add("origin", origin);

        JsonArray size = new JsonArray();
        size.add(round((maxX - minX) * 16f));
        size.add(round((maxY - minY) * 16f));
        size.add(round((maxZ - minZ) * 16f));
        node.add("size", size);

        if (rotated) {
            JsonArray rotation = new JsonArray();
            rotation.add(round(-degrees(Math.atan2(rot[2][1], rot[2][2]))));
            rotation.add(round(-degrees(Math.asin(clamp(-rot[2][0])))));
            rotation.add(round(degrees(Math.atan2(rot[1][0], rot[0][0]))));
            node.add("rotation", rotation);
            JsonArray pivot = new JsonArray();
            pivot.add(round(-centerX * 16f));
            pivot.add(round(centerY * 16f));
            pivot.add(round(centerZ * 16f));
            node.add("pivot", pivot);
        }

        JsonObject uv = new JsonObject();
        for (int index = 0; index < cube.faces.size() && index < FACE_NAMES.length; index++) {
            RawYsmModel.RawFace face = cube.faces.get(index);
            // v3 容器里 6 个面的顺序是固定的（west/east/north/south/up/down），
            // 不能靠法线去猜——旋转过的方块法线不精确，猜不出来就会丢 UV。
            String name = FACE_NAMES[index];
            if (uv.has(name)) {
                continue;
            }
            float u0 = face.u[0] * srcW, u1 = face.u[1] * srcW;
            float v0 = face.v[0] * srcH, v1 = face.v[2] * srcH;
            JsonObject faceNode = new JsonObject();
            JsonArray uvArray = new JsonArray();
            uvArray.add(round(Math.min(u0, u1)));
            uvArray.add(round(Math.min(v0, v1)));
            faceNode.add("uv", uvArray);
            JsonArray uvSize = new JsonArray();
            uvSize.add(round(Math.abs(u1 - u0)));
            uvSize.add(round(Math.abs(v1 - v0)));
            faceNode.add("uv_size", uvSize);
            uv.add(name, faceNode);
        }
        // 老容器里有个别方块只存了 2 个面，加载器要求 6 个面都在，缺的补成零面积，
        // 否则整只模型会因为 "Missing texture uv" 被丢弃。
        for (String faceName : FACE_NAMES) {
            if (uv.has(faceName)) {
                continue;
            }
            JsonObject faceNode = new JsonObject();
            JsonArray uvArray = new JsonArray();
            uvArray.add(0);
            uvArray.add(0);
            faceNode.add("uv", uvArray);
            JsonArray uvSize = new JsonArray();
            uvSize.add(0);
            uvSize.add(0);
            faceNode.add("uv_size", uvSize);
            uv.add(faceName, faceNode);
        }
        node.add("uv", uv);
        return node;
    }

    /** Expresses a baked face normal in the cube's own axes so it maps onto a Bedrock face name. */
    private static float[] localNormal(float[][] rot, float[] normal) {
        if (rot == null) {
            return normal;
        }
        return new float[]{
                rot[0][0] * normal[0] + rot[1][0] * normal[1] + rot[2][0] * normal[2],
                rot[0][1] * normal[0] + rot[1][1] * normal[1] + rot[2][1] * normal[2],
                rot[0][2] * normal[0] + rot[1][2] * normal[1] + rot[2][2] * normal[2]
        };
    }

    private static final String[] FACE_ORDER = {"west", "east", "north", "south", "up", "down"};

    /**
     * Columns are the world direction of the local +X / +Y / +Z axes, recovered from the baked
     * face normals (east, up, south). Returns null when the normals are not an orthonormal basis.
     */
    private static float[][] localBasis(RawYsmModel.RawCube cube) {
        if (cube.faces.size() < 6) {
            return null;
        }
        float[] ex = cube.faces.get(1).normal;
        float[] ey = cube.faces.get(4).normal;
        float[] ez = cube.faces.get(3).normal;
        if (ex == null || ey == null || ez == null) {
            return null;
        }
        if (Math.abs(dot(ex, ey)) > 1e-2 || Math.abs(dot(ex, ez)) > 1e-2
                || Math.abs(dot(ey, ez)) > 1e-2) {
            return null;
        }
        return new float[][]{
                {ex[0], ey[0], ez[0]},
                {ex[1], ey[1], ez[1]},
                {ex[2], ey[2], ez[2]}
        };
    }

    private static double dot(float[] a, float[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static boolean isIdentity(float[][] rot) {
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                double expected = i == j ? 1 : 0;
                if (Math.abs(rot[i][j] - expected) > 1e-3) {
                    return false;
                }
            }
        }
        return true;
    }

    private static double clamp(double value) {
        return Math.max(-1, Math.min(1, value));
    }

    private static double degrees(double radians) {
        return radians * 180.0 / Math.PI;
    }

    private static boolean isAxisAligned(float[] normal) {
        float max = Math.max(Math.abs(normal[0]), Math.max(Math.abs(normal[1]), Math.abs(normal[2])));
        return max > 0.99f;
    }

    private static String faceName(float[] normal) {
        if (Math.abs(normal[2] + 1) < 0.01f) return "north";
        if (Math.abs(normal[2] - 1) < 0.01f) return "south";
        if (Math.abs(normal[0] - 1) < 0.01f) return "east";
        if (Math.abs(normal[0] + 1) < 0.01f) return "west";
        if (Math.abs(normal[1] - 1) < 0.01f) return "up";
        if (Math.abs(normal[1] + 1) < 0.01f) return "down";
        return null;
    }

    private static void writeTexture(RawYsmModel.RawTexture tex, Path dir) throws IOException {
        if (tex == null || tex.data == null || tex.data.length == 0) {
            return;
        }
        String name = tex.name == null || tex.name.isEmpty() ? "skin" : tex.name;
        name = name.replace('\\', '_').replace('/', '_');
        if (tex.imageFormat == -1) {
            // format <= 15 的老容器：贴图是未压缩的 RGBA 原始像素
            writeRgbaTexture(tex, dir.resolve(name + ".png"));
            return;
        }
        String extension = switch (tex.imageFormat) {
            case 2 -> ".png";
            case 3 -> ".jpg";
            case 4 -> ".webp";
            case 5 -> ".avif";
            default -> null;
        };
        if (extension == null) {
            System.err.println("texture " + tex.name + " has unsupported format " + tex.imageFormat);
            return;
        }
        Files.write(dir.resolve(name + extension), tex.data);
    }

    /** 把 RGBA 原始像素编码成 PNG（老版 .ysm 的贴图只有这种走法）。 */
    private static void writeRgbaTexture(RawYsmModel.RawTexture tex, Path target) throws IOException {
        int width = (int) tex.width;
        int height = (int) tex.height;
        byte[] data = tex.data;
        if (width <= 0 || height <= 0 || data.length < 4L * width * height) {
            // 尺寸不可信时按正方形推断
            int side = (int) Math.round(Math.sqrt(data.length / 4.0));
            if (side > 0 && side * side * 4 == data.length) {
                width = side;
                height = side;
            } else {
                System.err.println("texture " + tex.name + " has unusable RGBA size: "
                        + tex.width + "x" + tex.height + " bytes=" + data.length);
                return;
            }
        }
        java.awt.image.BufferedImage image =
                new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        int[] pixels = new int[width * height];
        for (int i = 0, p = 0; i < pixels.length; i++, p += 4) {
            pixels[i] = (data[p] & 0xFF) << 24 | (data[p + 1] & 0xFF) << 16
                    | (data[p + 2] & 0xFF) << 8 | (data[p + 3] & 0xFF);
        }
        image.setRGB(0, 0, width, height, pixels, 0, width);
        javax.imageio.ImageIO.write(image, "png", target.toFile());
    }

    // ── animations ────────────────────────────────────────────────────────

    private static void writeAnimations(RawYsmModel model, Path out) throws IOException {
        Path dir = Files.createDirectories(out.resolve("animations"));
        if (model.mainEntity != null && model.mainEntity.animationFiles != null) {
            for (RawYsmModel.RawAnimationFile file : model.mainEntity.animationFiles.values()) {
                String name = animationFileName(file.animType);
                Files.writeString(dir.resolve(name + ".animation.json"), GSON.toJson(animationFileToJson(file)));
            }
        }
        writeSubAnimations(model.vehicles, dir.resolve("vehicle"));
        writeSubAnimations(model.projectiles, dir.resolve("projectile"));
    }

    private static void writeSubAnimations(Map<String, RawYsmModel.RawSubEntity> map, Path dir) throws IOException {
        for (Map.Entry<String, RawYsmModel.RawSubEntity> e : map.entrySet()) {
            if (e.getValue() == null || e.getValue().animationFiles == null || e.getValue().animationFiles.isEmpty()) {
                continue;
            }
            Files.createDirectories(dir);
            String safe = e.getKey().replace(':', '_');
            int index = 0;
            for (RawYsmModel.RawAnimationFile file : e.getValue().animationFiles.values()) {
                Files.writeString(dir.resolve(safe + (index == 0 ? "" : "_" + index) + ".animation.json"),
                        GSON.toJson(animationFileToJson(file)));
                index++;
            }
        }
    }

    private static String animationFileName(int type) {
        return switch (type) {
            case 1 -> "main";
            case 2 -> "arm";
            case 3 -> "extra";
            case 4 -> "tac";
            case 5 -> "arrow";
            case 6 -> "carryon";
            case 7 -> "parcool";
            case 8 -> "swem";
            case 9 -> "slashblade";
            case 10 -> "tlm";
            case 11 -> "fp.arm";
            case 12 -> "immersive_melodies";
            case 13 -> "irons_spell_books";
            default -> "unknown" + type;
        };
    }

    private static JsonObject animationFileToJson(RawYsmModel.RawAnimationFile file) {
        JsonObject root = new JsonObject();
        root.addProperty("format_version", "1.8.0");
        JsonObject animations = new JsonObject();
        for (Map.Entry<String, RawYsmModel.RawAnimation> e : file.animations.entrySet()) {
            animations.add(e.getKey(), animationToJson(e.getValue(), e.getKey()));
        }
        root.add("animations", animations);
        return root;
    }

    private static JsonObject animationToJson(RawYsmModel.RawAnimation anim, String name) {
        JsonObject node = new JsonObject();
        node.addProperty("loop", anim.loopMode == 1);
        if (anim.length > 0) {
            node.addProperty("animation_length", round(anim.length));
        }
        if (anim.blendWeight != null) {
            if (anim.blendWeight instanceof Number n) {
                node.addProperty("blend_weight", n.doubleValue());
            } else {
                node.addProperty("blend_weight", String.valueOf(anim.blendWeight));
            }
        }
        JsonObject bones = new JsonObject();
        for (RawYsmModel.RawBoneAnimation boneAnim : anim.boneAnimations) {
            JsonObject bone = new JsonObject();
            addChannel(bone, "rotation", boneAnim.rotation);
            addChannel(bone, "position", boneAnim.position);
            addChannel(bone, "scale", boneAnim.scale);
            bones.add(boneAnim.boneName, bone);
        }
        node.add("bones", bones);
        if (anim.timelineEvents != null && !anim.timelineEvents.isEmpty()) {
            JsonObject timeline = new JsonObject();
            for (RawYsmModel.RawTimelineEvent event : anim.timelineEvents) {
                JsonArray list = new JsonArray();
                for (String s : event.events) {
                    list.add(s);
                }
                timeline.add(formatTime(event.timestamp), list);
            }
            node.add("timeline", timeline);
        }
        if (anim.soundEffects != null && !anim.soundEffects.isEmpty()) {
            JsonObject sounds = new JsonObject();
            for (RawYsmModel.RawSoundEffect effect : anim.soundEffects) {
                JsonObject entry = new JsonObject();
                entry.addProperty("effect", effect.effectName);
                sounds.add(formatTime(effect.timestamp), entry);
            }
            node.add("sound_effects", sounds);
        }
        return node;
    }

    private static void addChannel(JsonObject bone, String key, List<RawYsmModel.RawKeyframe> frames) {
        if (frames == null || frames.isEmpty()) {
            return;
        }
        JsonObject channel = new JsonObject();
        for (RawYsmModel.RawKeyframe frame : frames) {
            String time = formatTime(frame.timestamp);
            JsonArray post = toVector(frame.postData);
            JsonArray pre = toVector(frame.preData);
            if (frame.hasPreData && pre != null) {
                JsonObject entry = new JsonObject();
                entry.add("pre", pre);
                if (post != null) {
                    entry.add("post", post);
                }
                if (frame.interpolationMode == 1) {
                    entry.addProperty("lerp_mode", "catmullrom");
                }
                channel.add(time, entry);
            } else if (post != null) {
                if (frame.interpolationMode == 1) {
                    JsonObject entry = new JsonObject();
                    entry.add("post", post);
                    entry.addProperty("lerp_mode", "catmullrom");
                    channel.add(time, entry);
                } else {
                    channel.add(time, post);
                }
            }
        }
        if (channel.size() > 0) {
            bone.add(key, channel);
        }
    }

    private static JsonArray toVector(Object[] data) {
        if (data == null) {
            return null;
        }
        JsonArray array = new JsonArray();
        boolean any = false;
        for (Object value : data) {
            if (value == null) {
                array.add(0);
                continue;
            }
            any = true;
            if (value instanceof Number n) {
                array.add(round(n.doubleValue()));
            } else {
                array.add(String.valueOf(value));
            }
        }
        return any ? array : null;
    }

    // ── animation controllers ─────────────────────────────────────────────

    private static void writeControllers(RawYsmModel model, Path out) throws IOException {
        Path dir = Files.createDirectories(out.resolve("controller"));
        if (model.mainEntity == null || model.mainEntity.animationControllerFiles == null) {
            return;
        }
        int index = 0;
        for (RawYsmModel.RawAnimationControllerFile file : model.mainEntity.animationControllerFiles) {
            String name = file.name == null || file.name.isEmpty() ? "controllers" + index : file.name;
            Files.writeString(dir.resolve(safeName(name) + ".animation_controllers.json"), GSON.toJson(controllerFileToJson(file)));
            index++;
        }
    }

    private static JsonObject controllerFileToJson(RawYsmModel.RawAnimationControllerFile file) {
        JsonObject root = new JsonObject();
        root.addProperty("format_version", "1.19.0");
        JsonObject controllers = new JsonObject();
        for (Map.Entry<String, RawYsmModel.RawAnimationController> e : file.controllers.entrySet()) {
            RawYsmModel.RawAnimationController controller = e.getValue();
            JsonObject node = new JsonObject();
            if (controller.initialState != null) {
                node.addProperty("initial_state", controller.initialState);
            }
            JsonObject states = new JsonObject();
            for (RawYsmModel.RawControllerState state : controller.states) {
                JsonObject stateNode = new JsonObject();
                JsonArray animations = new JsonArray();
                for (Map.Entry<String, String> anim : state.animations.entrySet()) {
                    animations.add(anim.getKey());
                }
                stateNode.add("animations", animations);
                if (!state.transitions.isEmpty()) {
                    JsonArray transitions = new JsonArray();
                    for (Map.Entry<String, String> transition : state.transitions.entrySet()) {
                        JsonObject t = new JsonObject();
                        t.addProperty(transition.getKey(), transition.getValue());
                        transitions.add(t);
                    }
                    stateNode.add("transitions", transitions);
                }
                if (!state.onEntry.isEmpty()) {
                    stateNode.add("on_entry", strings(state.onEntry));
                }
                if (!state.onExit.isEmpty()) {
                    stateNode.add("on_exit", strings(state.onExit));
                }
                if (!state.soundEffects.isEmpty()) {
                    stateNode.add("sound_effects", strings(state.soundEffects));
                }
                if (state.blendTransitions.isEmpty()) {
                    stateNode.addProperty("blend_transition", state.blendTransitionValue);
                } else {
                    JsonObject blend = new JsonObject();
                    for (Map.Entry<Float, Float> point : state.blendTransitions.entrySet()) {
                        blend.addProperty(String.valueOf(point.getKey()), point.getValue());
                    }
                    stateNode.add("blend_transition", blend);
                }
                if (state.blendViaShortestPath) {
                    stateNode.addProperty("blend_via_shortest_path", true);
                }
                states.add(state.name, stateNode);
            }
            node.add("states", states);
            controllers.add(e.getKey(), node);
        }
        root.add("animation_controllers", controllers);
        return root;
    }

    // ── misc payloads ─────────────────────────────────────────────────────

    private static void writeData(Map<String, RawYsmModel.RawDataFile> files, Path dir, String extension) throws IOException {
        for (Map.Entry<String, RawYsmModel.RawDataFile> e : files.entrySet()) {
            RawYsmModel.RawDataFile file = e.getValue();
            if (file == null || file.data == null) {
                continue;
            }
            Files.createDirectories(dir);
            String name = safeName(e.getKey());
            if (!name.toLowerCase().endsWith(extension)) {
                name = name + extension;
            }
            Files.write(dir.resolve(name), file.data);
        }
    }

    private static void writeLanguages(Map<String, RawYsmModel.RawLanguageFile> files, Path dir) throws IOException {
        for (Map.Entry<String, RawYsmModel.RawLanguageFile> e : files.entrySet()) {
            RawYsmModel.RawLanguageFile file = e.getValue();
            if (file == null || file.data == null) {
                continue;
            }
            Files.createDirectories(dir);
            JsonObject node = new JsonObject();
            for (Map.Entry<String, String> entry : file.data.entrySet()) {
                node.addProperty(entry.getKey(), entry.getValue());
            }
            Files.writeString(dir.resolve(safeName(e.getKey()) + ".json"), GSON.toJson(node));
        }
    }

    private static void writeMetadata(RawYsmModel model, Path out) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("spec", 2);

        RawYsmModel.RawMetadata meta = model.metadata;
        JsonObject metadata = new JsonObject();
        if (meta != null) {
            metadata.addProperty("name", meta.name);
            if (meta.tips != null && !meta.tips.isEmpty()) {
                metadata.addProperty("tips", meta.tips);
            }
            JsonObject license = new JsonObject();
            license.addProperty("type", meta.licenseType == null || meta.licenseType.isEmpty() ? "All Rights Reserved" : meta.licenseType);
            if (meta.licenseDescription != null && !meta.licenseDescription.isEmpty()) {
                license.addProperty("description", meta.licenseDescription);
            }
            metadata.add("license", license);
            JsonArray authors = new JsonArray();
            for (RawYsmModel.RawMetadata.Author author : meta.authors) {
                JsonObject a = new JsonObject();
                a.addProperty("name", author.name);
                if (author.role != null && !author.role.isEmpty()) a.addProperty("role", author.role);
                if (author.comment != null && !author.comment.isEmpty()) a.addProperty("comment", author.comment);
                if (!author.contacts.isEmpty()) {
                    JsonObject contacts = new JsonObject();
                    for (Map.Entry<String, String> c : author.contacts.entrySet()) {
                        contacts.addProperty(c.getKey(), c.getValue());
                    }
                    a.add("contact", contacts);
                }
                authors.add(a);
            }
            metadata.add("authors", authors);
            if (!meta.links.isEmpty()) {
                JsonObject links = new JsonObject();
                for (Map.Entry<String, String> l : meta.links.entrySet()) {
                    links.addProperty(l.getKey(), l.getValue());
                }
                metadata.add("link", links);
            }
        }
        root.add("metadata", metadata);

        RawYsmModel.RawProperties props = model.properties;
        JsonObject properties = new JsonObject();
        if (props != null) {
            if (!props.extraAnimations.isEmpty()) {
                JsonObject extra = new JsonObject();
                for (Map.Entry<String, String> e : props.extraAnimations.entrySet()) {
                    String key = e.getKey();
                    if (key.matches("\\d+")) {
                        key = "extra" + key;
                    }
                    extra.addProperty(key, e.getValue());
                }
                properties.add("extra_animation", extra);
            }
            if (props.previewAnimation != null && !props.previewAnimation.isEmpty()) {
                properties.addProperty("preview_animation", props.previewAnimation);
            }
            properties.addProperty("width_scale", props.widthScale);
            properties.addProperty("height_scale", props.heightScale);
            properties.addProperty("free", props.isFree);
            properties.addProperty("render_layers_first", props.renderLayersFirst);
            properties.addProperty("all_cutout", true);
            properties.addProperty("disable_preview_rotation", props.disablePreviewRotation);
            properties.addProperty("gui_no_lighting", props.guiNoLighting);
            properties.addProperty("merge_multiline_expr", props.mergeMultilineExpr);
        }
        root.add("properties", properties);

        JsonObject files = new JsonObject();
        JsonObject player = new JsonObject();
        if (model.mainEntity != null && model.mainEntity.mainModel != null) {
            JsonObject modelNode = new JsonObject();
            modelNode.addProperty("main", "models/main.json");
            if (model.mainEntity.armModel != null) {
                modelNode.addProperty("arm", "models/arm.json");
            }
            player.add("model", modelNode);
        }
        if (model.mainEntity != null && model.mainEntity.animationFiles != null) {
            JsonObject animNode = new JsonObject();
            List<String> written = new ArrayList<>();
            for (RawYsmModel.RawAnimationFile file : model.mainEntity.animationFiles.values()) {
                animNode.addProperty(animationFileNameForJson(file.animType), "animations/" + animationFileName(file.animType) + ".animation.json");
                written.add(animationFileName(file.animType));
            }
            if (animNode.size() > 0) {
                player.add("animation", animNode);
            }
        }
        if (model.mainEntity != null && model.mainEntity.animationControllerFiles != null && !model.mainEntity.animationControllerFiles.isEmpty()) {
            JsonArray controllers = new JsonArray();
            int index = 0;
            for (RawYsmModel.RawAnimationControllerFile file : model.mainEntity.animationControllerFiles) {
                String name = file.name == null || file.name.isEmpty() ? "controllers" + index : file.name;
                controllers.add("controller/" + safeName(name) + ".animation_controllers.json");
                index++;
            }
            player.add("animation_controllers", controllers);
        }
        if (model.mainEntity != null && model.mainEntity.textures != null && !model.mainEntity.textures.isEmpty()) {
            JsonArray tex = new JsonArray();
            for (RawYsmModel.RawTexture texture : model.mainEntity.textures.values()) {
                if (texture != null && texture.data != null && texture.data.length > 0) {
                    String name = texture.name == null || texture.name.isEmpty() ? "skin" : texture.name;
                    tex.add("textures/" + name + ".png");
                }
            }
            if (tex.size() > 0) {
                player.add("texture", tex);
            }
        }
        files.add("player", player);

        JsonObject vehicles = new JsonObject();
        for (Map.Entry<String, RawYsmModel.RawSubEntity> e : model.vehicles.entrySet()) {
            JsonObject node = new JsonObject();
            node.addProperty("model", "models/" + e.getKey().replace(':', '_') + ".json");
            vehicles.add(e.getKey(), node);
        }
        if (vehicles.size() > 0) {
            files.add("vehicles", vehicles);
        }
        JsonArray projectiles = new JsonArray();
        for (Map.Entry<String, RawYsmModel.RawSubEntity> e : model.projectiles.entrySet()) {
            JsonObject node = new JsonObject();
            JsonArray match = new JsonArray();
            match.add(e.getKey());
            node.add("match", match);
            node.addProperty("model", "models/" + e.getKey().replace(':', '_') + ".json");
            String texPath = firstTexturePath(e.getValue(), model);
            if (texPath != null) {
                node.addProperty("texture", texPath);
            }
            projectiles.add(node);
        }
        if (projectiles.size() > 0) {
            files.add("projectiles", projectiles);
        }
        root.add("files", files);
        Files.writeString(out.resolve("ysm.json"), GSON.toJson(root));
    }

    private static String animationFileNameForJson(int type) {
        return switch (type) {
            case 1 -> "main";
            case 2 -> "arm";
            case 3 -> "extra";
            case 4 -> "tac";
            case 5 -> "arrow";
            case 6 -> "carryon";
            case 7 -> "parcool";
            case 8 -> "swem";
            case 9 -> "slashblade";
            case 10 -> "tlm";
            case 11 -> "fp_arm";
            case 12 -> "immersive_melodies";
            case 13 -> "irons_spell_books";
            default -> "unknown" + type;
        };
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private static JsonArray vector(float[] values) {
        JsonArray array = new JsonArray();
        if (values == null) {
            array.add(0);
            array.add(0);
            array.add(0);
            return array;
        }
        for (float value : values) {
            array.add(round(value));
        }
        return array;
    }

    private static JsonArray strings(List<String> values) {
        JsonArray array = new JsonArray();
        for (String value : values) {
            array.add(value);
        }
        return array;
    }

    private static String formatTime(float time) {
        double rounded = Math.round(time * 10000.0) / 10000.0;
        if (rounded == Math.floor(rounded)) {
            return String.valueOf((long) rounded);
        }
        return String.valueOf(rounded);
    }

    private static Number round(double value) {
        double rounded = Math.round(value * 100000.0) / 100000.0;
        if (rounded == Math.floor(rounded) && Math.abs(rounded) < 1e9) {
            return (long) rounded;
        }
        return rounded;
    }

    private static String safeName(String name) {
        return name.replace(':', '_').replace('/', '_').replace('\\', '_');
    }

    /** Pretty printing wrapper so the JSON stays readable and diff-able. */
    private static final class GsonPretty {
        private final com.google.gson.Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

        String toJson(com.google.gson.JsonElement element) {
            return gson.toJson(element);
        }
    }
}
