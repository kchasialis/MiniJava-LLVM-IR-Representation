package types;
import java.lang.reflect.Field;
import java.util.*;
import java.util.AbstractMap.SimpleEntry;
import visitors.ClassDefinitions;
import static java.util.Objects.requireNonNull;

public class ClassBody {
    /*Some brilliant guys in Java decided it was a good idea not to provide Set interface with a get() method
      So, we have to do something like this...*/
    private Map<ClassField,ClassField> fields;
    private Map<ClassMethodDeclaration, ClassMethodBody> methods;
    private Map<ClassField, Integer> fieldOffsets;
    private Map<String, Integer> methodOffsets;
    private Map<String, Integer> realOffsets;
    private final Optional<String> extendsClassName;
    private final Map<String, Integer> sizes;
    private Integer currentFieldOffset;
    private Integer currentMethodOffset;

    public ClassBody() {
        this.methods = new LinkedHashMap<ClassMethodDeclaration, ClassMethodBody>();
        this.fields = new HashMap<ClassField, ClassField>();
        this.fieldOffsets = new LinkedHashMap<ClassField, Integer>();
        this.methodOffsets = new LinkedHashMap<String, Integer>();
        this.realOffsets = new LinkedHashMap<String, Integer>();
        this.extendsClassName = Optional.empty();
        this.sizes = new HashMap<String, Integer>() {{
           put("int", 4);
           put("boolean", 1);
           put("int[]", 8);
           put("boolean[]", 8);
        }};
        this.currentFieldOffset = 0;
        this.currentMethodOffset = 0;
    }

    public ClassBody(String extendsClassName, ClassDefinitions classDefinitions) {
        this.methods = new LinkedHashMap<ClassMethodDeclaration, ClassMethodBody>();
        this.fields = new HashMap<ClassField, ClassField>();
        this.fieldOffsets = new LinkedHashMap<ClassField, Integer>();
        this.methodOffsets = new LinkedHashMap<String, Integer>();
        this.realOffsets = new LinkedHashMap<String, Integer>();
        this.extendsClassName = Optional.of(requireNonNull(extendsClassName, "Extends Class Name should be not null"));
        this.sizes = new HashMap<String, Integer>() {{
            put("int", 4);
            put("boolean", 1);
            put("int[]", 8);
            put("boolean[]", 8);
        }};

        this.currentFieldOffset = getStartingOffsetOfField(this, classDefinitions);
        this.currentMethodOffset = getStartingOffsetOfMethod(this, classDefinitions);
    }

    public Map<String, Integer> getRealOffsets() {
        return this.realOffsets;
    }

    public void addRealOffset(String methodIdentifier, Integer offset) {
        this.realOffsets.put(methodIdentifier, offset);
    }

    public String getExtendsClassName() {
        return this.extendsClassName.orElse(null);
    }

    public Map<ClassField,ClassField> getFields() {
        return this.fields;
    }

    public Map<ClassMethodDeclaration, ClassMethodBody> getMethods() {
        return this.methods;
    }

    private Integer getStartingOffsetOfField(ClassBody classBody, ClassDefinitions classDefinitions) {
        if (classBody.getExtendsClassName() != null) {
            ClassBody baseClassBody = classDefinitions.getDefinitions().get(new ClassIdentifier(classBody.getExtendsClassName()));
            Map<ClassField, Integer> tempFieldOffsets = baseClassBody.fieldOffsets;

            if (tempFieldOffsets.size() > 0) {
                List<Map.Entry<ClassField, Integer>> entryList = new ArrayList<Map.Entry<ClassField, Integer>>(tempFieldOffsets.entrySet());
                Map.Entry<ClassField, Integer> lastEntry = entryList.get(entryList.size() - 1);

                Integer lastFieldSize = sizeOf(lastEntry.getKey().getType());
                return lastFieldSize + lastEntry.getValue();
            }
            else if (baseClassBody.getExtendsClassName() != null) {
                return getStartingOffsetOfField(baseClassBody, classDefinitions);
            }
            else {
                return 0;
            }
        }
        else {
            return 0;
        }
    }

    private Integer getStartingOffsetOfMethod(ClassBody classBody, ClassDefinitions classDefinitions) {
        if (classBody.getExtendsClassName() != null) {
            ClassBody baseClassBody = classDefinitions.getDefinitions().get(new ClassIdentifier(classBody.getExtendsClassName()));
            Map<String, Integer> tempMethodOffsets = baseClassBody.methodOffsets;

            if (tempMethodOffsets.size() > 0) {
                List<Map.Entry<String, Integer>> entryList = new ArrayList<Map.Entry<String, Integer>>(tempMethodOffsets.entrySet());
                Map.Entry<String, Integer> lastEntry = entryList.get(entryList.size() - 1);

                return lastEntry.getValue() + 8;
            }
            else if (baseClassBody.getExtendsClassName() != null) {
                return getStartingOffsetOfMethod(baseClassBody, classDefinitions);
            }
            else {
                return 0;
            }
        }
        else {
            return 0;
        }
    }

    private Integer sizeOf(String type) {
        Integer retval = this.sizes.get(type);
        //If retval == null it means its an instance of an object
        if (retval == null) {
            return 8;
        }
        return retval;
    }

    public Map<ClassField, Integer> getFieldOffsets() {
        return this.fieldOffsets;
    }

    public Map<String, Integer> getMethodOffsets() {
        return this.methodOffsets;
    }

    public void addFieldOffset(ClassField field) {
        this.fieldOffsets.put(field, this.currentFieldOffset);
        this.currentFieldOffset += sizeOf(field.getType());
    }

    public void addMethodOffset(ClassMethodDeclaration classMethodDeclaration) {
        String identifier = classMethodDeclaration.getIdentifier();
        this.methodOffsets.put(identifier, this.currentMethodOffset);
        this.currentMethodOffset += 8;
    }

    public void addField(ClassField field) {
        this.fields.put(field, field);
    }

    public void addMethod(ClassMethodDeclaration classMethodDeclaration, ClassMethodBody classMethodBody) {
        this.methods.put(classMethodDeclaration, classMethodBody);
    }
}