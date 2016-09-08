package org.jboss.fuse.qa.karaf.leak;

import org.netbeans.lib.profiler.heap.Heap;
import org.netbeans.lib.profiler.heap.HeapFactory;
import org.netbeans.lib.profiler.heap.Instance;
import org.netbeans.lib.profiler.heap.JavaClass;
import org.netbeans.lib.profiler.heap.PrimitiveArrayInstance;
import org.netbeans.lib.profiler.heap.Value;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Created by jludvice on 9/6/16.
 */
public class Main {
    public static final String SYMBOLIC_NAME_FIELD = "m_symbolicName";
    public static String BUNDLE_REVISION_CLASS_NAME = "org.apache.felix.framework.BundleRevisionImpl";
    public static String BUNDLE_WIRING_CLASS_NAME = "org.apache.felix.framework.BundleWiringImpl";

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.out.println("pass path to heap dump as argument");
            System.exit(1);
        }
        String fileName = args[0];
        Path heapDumpPath = Paths.get(fileName).toAbsolutePath();

        if (!Files.exists(heapDumpPath)) {
            System.out.println("file doesn't exist: " + heapDumpPath);
            System.exit(1);
        }

        // load heap dump to netbeans profiler
        System.out.println("loading file " + heapDumpPath);
        Heap h = HeapFactory.createHeap(heapDumpPath.toFile());

        // filter all references of given class to instance
        Predicate<? super Value> wiringFilter = reference -> BUNDLE_WIRING_CLASS_NAME.equals(reference.getDefiningInstance().getJavaClass().getName());
        // filter instances which have more than 1 reference to itself
        Predicate<? super Instance> revisionFilter = i -> ((List<Value>) i.getReferences()).stream().filter(wiringFilter).count() > 1;

        // get java class by name
        JavaClass revision = h.getJavaClassByName(BUNDLE_REVISION_CLASS_NAME);

        // list instances of given class && filter to those with more than 1 reference pointing to it
        List<Instance> revisionInstances = ((List<Instance>) revision.getInstances()).parallelStream()
                .filter(revisionFilter)
                .collect(Collectors.toList());

        for (Instance instance : revisionInstances) {
            String sn = getSymbolicName(instance);

            List<Value> wirings = ((List<Value>) instance.getReferences()).stream()
                    .filter(wiringFilter)
                    .sorted((x, y) -> Long.compare(x.getDefiningInstance().getInstanceId(), y.getDefiningInstance().getInstanceId()))
                    .collect(Collectors.toList());

            // get reference with lowest id
            Instance wiringInstance = wirings.get(0).getDefiningInstance();
            // check if there is path to nearest GCRoot
            Instance root = wiringInstance.getNearestGCRootPointer();
            if (root == null) {
                // if no - not interesting, skip this instance
                continue;
            }
            // find out path to root
            List<Instance> rootPath = new ArrayList<>();
            while (!root.isGCRoot()) {
                rootPath.add(root);
                root = root.getNearestGCRootPointer();
            }

            System.out.println("bundle symbolic name: " + sn);
            String type = "this";
            for (int i = 0; i < rootPath.size(); i++) {
                Instance node = rootPath.get(i);
                long nextId = -1;
                if (i + 1 < rootPath.size()) {
                    // not last element
                    nextId = rootPath.get(i + 1).getInstanceId();
                }
                long finalNextId = nextId;
                // find reference to next instance on GC path pointing to this one
                Optional<String> references = ((List<Value>) node.getReferences()).stream()
                        .filter(r -> finalNextId == r.getDefiningInstance().getInstanceId())
                        .map(r -> r.getClass().getSimpleName())
                        .findFirst();

                String className = node.getJavaClass().getName();

                int n = 1 + i * 2;
                String indent = String.format("%" + n + "s <- ", "");
                System.out.println(String.format("%s (%s) - class %s", indent, type, className));
                type = references.orElse("not found");
            }
        }
    }

    /**
     * Get bundle symbolic name from instance of java class from heap dump.
     *
     * @param i Instance of java class
     * @return symbolic name of bundle containing this class
     */
    public static String getSymbolicName(Instance i) {
        Instance symbolicNameInstance = (Instance) i.getValueOfField(SYMBOLIC_NAME_FIELD);
        PrimitiveArrayInstance snValue = (PrimitiveArrayInstance) symbolicNameInstance.getValueOfField("value");
        return String.join("", snValue.getValues());
    }
}

