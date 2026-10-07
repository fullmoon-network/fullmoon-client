package dev.fullmoon.client.mixin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Checks, against the game's own class files, that everything the mixins aim at exists: the
 * method an injector names, the call a redirector replaces (owner, name and descriptor), the field
 * a shadow reads. The mixins are not applied here, only read; a miss is what the game reports at
 * start-up as a critical injection failure, after which the class does not load at all.
 */
final class MixinTargetsTest {
    private static final Pattern TARGET = Pattern.compile("L([^;]+);([^(]+)(\\(.*)");

    private static ClassNode read(String internalName) throws IOException {
        try (InputStream in = MixinTargetsTest.class.getClassLoader().getResourceAsStream(internalName + ".class")) {
            assertTrue(in != null, internalName + " is on the class path");
            ClassNode node = new ClassNode();
            new ClassReader(in).accept(node, ClassReader.SKIP_FRAMES);
            return node;
        }
    }

    private static List<String> mixinClasses() throws IOException {
        try (InputStream in = MixinTargetsTest.class.getClassLoader().getResourceAsStream("fullmoon.mixins.json")) {
            JsonElement root = JsonParser.parseString(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            String pkg = root.getAsJsonObject().get("package").getAsString().replace('.', '/');
            List<String> names = new ArrayList<>();
            root.getAsJsonObject().getAsJsonArray("client").forEach(e -> names.add(pkg + "/" + e.getAsString()));
            return names;
        }
    }

    private static Object value(AnnotationNode annotation, String name) {
        if (annotation.values != null) {
            for (int i = 0; i < annotation.values.size(); i += 2) {
                if (annotation.values.get(i).equals(name)) {
                    return annotation.values.get(i + 1);
                }
            }
        }
        return null;
    }

    private static List<AnnotationNode> invisible(List<AnnotationNode> annotations, String descriptor) {
        List<AnnotationNode> out = new ArrayList<>();
        if (annotations != null) {
            for (AnnotationNode annotation : annotations) {
                if (annotation.desc.equals(descriptor)) {
                    out.add(annotation);
                }
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<String> methodNames(AnnotationNode injector) {
        return (List<String>) value(injector, "method");
    }

    @Test
    void theMixinConfigListsFourMixins() throws IOException {
        assertEquals(4, mixinClasses().size());
    }

    @Test
    void everyInjectorAndRedirectorFindsItsTarget() throws Exception {
        int checked = 0;
        for (String mixin : mixinClasses()) {
            ClassNode mixinNode = read(mixin);
            AnnotationNode mixinAnnotation = invisible(mixinNode.invisibleAnnotations, "Lorg/spongepowered/asm/mixin/Mixin;").get(0);
            @SuppressWarnings("unchecked")
            List<Type> targets = (List<Type>) value(mixinAnnotation, "value");
            assertEquals(1, targets.size(), mixin);
            ClassNode target = read(targets.get(0).getInternalName());

            for (MethodNode handler : mixinNode.methods) {
                List<AnnotationNode> injectors = new ArrayList<>();
                injectors.addAll(invisible(handler.invisibleAnnotations, "Lorg/spongepowered/asm/mixin/injection/Inject;"));
                injectors.addAll(invisible(handler.invisibleAnnotations, "Lorg/spongepowered/asm/mixin/injection/Redirect;"));
                for (AnnotationNode injector : injectors) {
                    for (String name : methodNames(injector)) {
                        List<MethodNode> candidates = target.methods.stream().filter(m -> m.name.equals(name)).toList();
                        assertEquals(1, candidates.size(), mixin + "." + handler.name + " targets " + name + " in " + target.name);
                        Object at = value(injector, "at");
                        if (at instanceof AnnotationNode atNode && "INVOKE".equals(value(atNode, "value"))) {
                            Matcher matcher = TARGET.matcher((String) value(atNode, "target"));
                            assertTrue(matcher.matches(), mixin + "." + handler.name + " has a well-formed target");
                            boolean found = false;
                            for (AbstractInsnNode insn : candidates.get(0).instructions) {
                                if (insn instanceof MethodInsnNode call && call.owner.equals(matcher.group(1))
                                    && call.name.equals(matcher.group(2)) && call.desc.equals(matcher.group(3))) {
                                    found = true;
                                }
                            }
                            assertTrue(found, mixin + "." + handler.name + " finds " + matcher.group(0) + " in " + target.name + "." + name);
                        }
                        checked++;
                    }
                }
            }
            for (FieldNode field : mixinNode.fields) {
                if (!invisible(field.invisibleAnnotations, "Lorg/spongepowered/asm/mixin/Shadow;").isEmpty()) {
                    assertTrue(target.fields.stream().anyMatch(f -> f.name.equals(field.name)),
                        mixin + " shadows " + field.name + ", which " + target.name + " has");
                }
            }
        }
        assertFalse(checked == 0, "the check reached at least one injector");
        assertEquals(8, checked, "FontManager 1, FontSet 2, definition 4, provider 1");
    }
}
