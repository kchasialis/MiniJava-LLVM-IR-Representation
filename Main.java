import syntaxtree.Goal;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import visitors.*;

class Main {
    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: java Driver <inputFile>");
            System.exit(-1);
        }
        FileInputStream fis = null;
        for (int i = 0 ; i < args.length ; i++) {
            try {
                fis = new FileInputStream(args[i]);
                MiniJavaParser mjparser = new MiniJavaParser(fis);
                Goal root = mjparser.Goal();

                ClassDefinitions classDefs = new ClassDefinitions();
                root.accept(classDefs, null);

                Path path = Paths.get(args[i]);
                Path fileName = path.getFileName();
                IntermidiateRepresentation intermidiateRepresentation = new IntermidiateRepresentation(classDefs, fileName.toString());
                boolean failed = classDefs.getErrorMessages().size() > 0;

                root.accept(intermidiateRepresentation, null);
                /*try {
                    root.accept(intermidiateRepresentation, null);
                } catch (RuntimeException re) {
                    classDefs.getErrorMessages().add(re.getMessage());
                    failed = true;
                }*/

                if (failed) {
                    printErrors(classDefs.getErrorMessages());
                    System.err.println("LLVM Code Generation failed");
                    System.err.println();
                }
            } catch (ParseException ex) {
                System.err.println(ex.getMessage());
            } catch (FileNotFoundException ex) {
                System.err.println(ex.getMessage());
            } finally {
                try {
                    if (fis != null) {
                        fis.close();
                    }
                } catch (IOException ex) {
                    System.err.println(ex.getMessage());
                }
            }
         }
    }

    public static void printErrors(List<String> errorMessages) {
        for (int i = 0 ; i < errorMessages.size(); i++) {
            System.err.println(errorMessages.get(i));
        }
    }
}