package dev.skidfuscator.obf.transform.impl.fixer;

import com.google.common.collect.Lists;
import dev.skidfuscator.obf.init.SkidSession;
import dev.skidfuscator.obf.skidasm.SkidGraph;
import dev.skidfuscator.obf.skidasm.SkidMethod;
import dev.skidfuscator.obf.transform.impl.flow.FlowPass;
import org.mapleir.asm.ClassNode;
import org.mapleir.flowgraph.ExceptionRange;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.objectweb.asm.Type;

import java.util.*;

public class ExceptionFixerPass implements FlowPass {
    @Override
    public void pass(SkidSession session, SkidMethod method) {
        for (SkidGraph methodNode : method.getMethodNodes()) {
            final ControlFlowGraph cfg = session.getCxt().getIRCache().get(methodNode.getNode());

            if (cfg == null)
                continue;

            for (ExceptionRange<BasicBlock> range : cfg.getRanges()) {
                if (range.getTypes().size() <= 1)
                    continue;

                final Stack<ClassNode> stack = new Stack<>();
                for (Type type : range.getTypes()) {
                    final ClassNode classNode = session.getCxt().getApplication().findClassNode(type.getClassName().replace(".", "/"));

                    if (classNode == null) {
                        System.err.println("[fatal] Could not find exception of name " + type.getClassName().replace(".", "/") + "! Skipping...");
                        continue;
                    }

                    final List<ClassNode> classNodeList = session.getCxt().getApplication().getClassTree().getAllParents(classNode);

                    if (stack.isEmpty()) {
                        stack.add(classNode);
                        stack.addAll(Lists.reverse(classNodeList));
                    } else {
                        final Stack<ClassNode> toIterate = new Stack<>();
                        toIterate.add(classNode);
                        toIterate.addAll(Lists.reverse(classNodeList));

                        runner: {
                            while (!stack.isEmpty()) {
                                for (ClassNode node : toIterate) {
                                    if (node.equals(stack.peek()))
                                        break runner;
                                }

                                stack.pop();
                            }

                            throw new IllegalStateException("Could not find common exception type between " + Arrays.toString(range.getTypes().toArray()));
                        }
                    }
                }

                range.setTypes(new HashSet<>(Collections.singleton(Type.getObjectType(stack.peek().getName()))));
            }
        }
    }

    @Override
    public String getName() {
        return "Exception Fixer";
    }
}
