package visitors;

import syntaxtree.*;
import visitor.GJDepthFirst;
import types.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.text.ParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.AbstractMap.SimpleEntry;

/**
 * A class method has a declaration and a body
 * 1) Declaration contains:
 * --1.1) type identifier parameters
 * 2) Body contains:
 * --2.1) Variable declarations (we only care for these now)
 * --2.2) Statements
 * --2.3) Expression
 * */
class Argument {
    public SimpleEntry<ClassMethodDeclaration, ClassMethodBody> currentMethod;
    public SimpleEntry<ClassIdentifier, ClassBody> currentClass;
    public Map<MethodParameter, MethodParameter> currentParameters;
    public Iterator<Map.Entry<MethodParameter, MethodParameter>> currentIterator;
    public int currentRegister;
    public int currentParameter;
    public int currentLabel;
    public boolean performCheck; //Let Identifier know that it should not do anything for type check, just accept
    public boolean isMethodDeclaration; //This means that if we see a VarDeclaration outside of a method, we should not allocate stack space
    public boolean produceCode; //Let the Identifier know that it should produce code for this identifier because its an expression, not just a name acccess
}


enum IdentifierOrigin {
    OBJECT,
    LOCAL,
}

/*This object type represents the current type we are currently working on*/
class ObjectType {
    public String identifier;
    public SimpleEntry<String, Set<String> > customType;
    public String primitiveType;

    /*this variable represents the origin of an identifier
     * object (means that its an object variable)
     * local (means that its a local variable)
     */
    public IdentifierOrigin identifierOrigin;
    public boolean isPrimitive;
    public int returnRegister;

    public ObjectType() {
        this.identifier = null;
        this.primitiveType = null;
        this.customType = null;
        this.isPrimitive = false;
        this.identifierOrigin = null;
        this.returnRegister = 0;
    }

    public ObjectType(String primitiveType) {
        this.isPrimitive = true;
        this.identifierOrigin = null;
        this.primitiveType = primitiveType;
        this.returnRegister = 0;
    }

    public String getType() {
        if (this.isPrimitive) {
            return this.primitiveType;
        }
        else {
            return this.customType.getKey();
        }
    }

    /**
     * This function creates the Set of classes that our object has a 'is-a' relationship.
     * For example.
     * class A {} ... class B extends A {} ... class C extends B{} ...
     * Then C is a custom type (primitiveType is null) and its Set (SimpleEntry<String, Set<String>>) representing 'is-a' relationship is the following:
     * Set = {C, B, A};
     */
    private static void fillCustomObject(ClassIdentifier classIdentifier, ClassDefinitions classDefinitions, ObjectType objectType) {
        String extendsIdentifier = classDefinitions.getDefinitions().get(classIdentifier).getExtendsClassName();
        if (extendsIdentifier != null) {
            objectType.customType.getValue().add(extendsIdentifier);
            fillCustomObject(new ClassIdentifier(extendsIdentifier), classDefinitions, objectType);
        }
    }

    public static ObjectType createCustomObject(String identifier, String objectName, ClassDefinitions classDefinitions, IdentifierOrigin identifierOrigin) {
        ObjectType returnObject = new ObjectType();
        returnObject.identifierOrigin = identifierOrigin;
        returnObject.identifier = identifier;
        returnObject.isPrimitive = false;
        returnObject.customType = new SimpleEntry<String, Set<String>>(objectName, new HashSet<String>());
        returnObject.customType.getValue().add(objectName);

        fillCustomObject(new ClassIdentifier(objectName), classDefinitions, returnObject);

        return returnObject;
    }

    private boolean isDerivedOf(ObjectType rhsObject) {
        return this.customType.getValue().contains(rhsObject.customType.getKey());
    }

    /**
     * IMPORTANT!!!
     * This wont work properly if the derived class is NOT the rhs object
     **/
    public boolean equals(ObjectType rhsObject) {

        if (!rhsObject.isPrimitive && !this.isPrimitive) {
            /*If both types are custom objects */
            return rhsObject.isDerivedOf(this);
        }
        else if (!rhsObject.isPrimitive) {
            return false;
        }
        else if (!this.isPrimitive) {
            return false;
        }
        else {
            /*If both types are primitives*/
            return rhsObject.primitiveType.equals(this.primitiveType);
        }
    }

    /**
     * "this" object must be a derived class of the method parameter
     */

    public boolean equals(MethodParameter methodParameter) {
        String type = methodParameter.getType();
        boolean isCustomObject = !type.equals("int") && !type.equals("int[]") && !type.equals("boolean") && !type.equals("boolean[]");

        if (isCustomObject && !this.isPrimitive) {
            /*If both types are custom objects check if the expression is a derived class of the declared parameter*/
            return this.customType.getValue().contains(type);
        }
        else if (isCustomObject) {
            return false;
        }
        else if (!this.isPrimitive) {
            return false;
        }
        else {
            /*If both types are primitives*/
            return type.equals(this.primitiveType);
        }
    }

    public boolean equals(String primitiveType) {
        if (this.primitiveType == null) {
            return false;
        }
        return this.primitiveType.equals(primitiveType);
    }

}

public class IntermidiateRepresentation extends GJDepthFirst<Object, Object> {

    private ClassDefinitions classDefinitions;
    private int currentLine;
    private int currentColumn;
    private PrintStream printStream;
    private Map<String, String> types;
    private final Map<String, Integer> sizes;

    public IntermidiateRepresentation(ClassDefinitions classDefinitions, String filename) throws FileNotFoundException  {
        this.classDefinitions = classDefinitions;
        this.currentLine = 1;
        this.currentColumn = 1;
        this.printStream = new PrintStream(new File(filename.contains(".java") ? filename.replace(".java", ".ll") : (filename + ".ll")));
        this.types = new HashMap<String, String>() {{
            put("int", "i32");
            put("boolean", "i1");
            put("int[]", "i32*");
            put("boolean[]", "i8*");
        }};
        this.sizes = new HashMap<String, Integer>() {{
            put("int", 4);
            put("boolean", 1);
            put("int[]", 8);
            put("boolean[]", 8);
        }};

        System.setOut(this.printStream);

        /*Print vtables*/
        String mainClassName = null;
        Iterator<Map.Entry<ClassIdentifier, ClassBody>> iterator = classDefinitions.getDefinitions().entrySet().iterator();
        int count = 0;
        while (iterator.hasNext()) {
            count++;
            Map.Entry<ClassIdentifier, ClassBody> value = iterator.next();

            if (count == 1) {
                mainClassName = value.getKey().getClassName();
                continue;
            }

            Map<ClassMethodDeclaration, String> classMethods = new LinkedHashMap<ClassMethodDeclaration, String>();
            getClassMethods(value.getValue(), value.getKey().getClassName(), classMethods);

            System.out.println("@." + value.getKey().getClassName() + "_vtable = global [" +
                    classMethods.size() + " x i8*] [");

            int currentOffset = 0;
            Iterator<Map.Entry<ClassMethodDeclaration, String>> classMethodsIterator = classMethods.entrySet().iterator();
            while (classMethodsIterator.hasNext()) {
                Map.Entry<ClassMethodDeclaration, String> classMethodValue = classMethodsIterator.next();

                value.getValue().addRealOffset(classMethodValue.getKey().getIdentifier(), currentOffset);

                System.out.print("\ti8* bitcast (" + getIRType(classMethodValue.getKey().getReturnType()) +
                        " (i8*");

                /*Print types of parameters*/
                for (MethodParameter methodParameter : classMethodValue.getKey().getParameters().keySet()) {
                    System.out.print("," + getIRType(methodParameter.getType()));
                }

                System.out.print(")* @" + classMethodValue.getValue() + "." + classMethodValue.getKey().getIdentifier()
                        + " to i8*)");

                if (classMethodsIterator.hasNext()) {
                    System.out.println(",");
                }
                else {
                    System.out.println("\n]\n");
                }

                currentOffset++;
            }
        }

        System.out.println("@." + mainClassName + "_vtable = global [0 x i8*] []\n");

        /*Print the first lines that are common for all programs*/
        System.out.println("\ndeclare i8* @calloc(i32, i32)\n" +
                "declare i32 @printf(i8*, ...)\n" +
                "declare void @exit(i32)\n" +
                "\n" +
                "@_cint = constant [4 x i8] c\"%d\\0a\\00\"\n" +
                "@_cOOB = constant [15 x i8] c\"Out of bounds\\0a\\00\"\n" +
                "define void @print_int(i32 %i) {\n" +
                "    %_str = bitcast [4 x i8]* @_cint to i8*\n" +
                "    call i32 (i8*, ...) @printf(i8* %_str, i32 %i)\n" +
                "    ret void\n" +
                "}\n" +
                "\n" +
                "define void @throw_oob() {\n" +
                "    %_str = bitcast [15 x i8]* @_cOOB to i8*\n" +
                "    call i32 (i8*, ...) @printf(i8* %_str)\n" +
                "    call void @exit(i32 1)\n" +
                "    ret void\n" +
                "}\n");

    }

    private void getClassMethods(ClassBody classBody, String className, Map<ClassMethodDeclaration, String> classMethods) {
        if (classBody.getExtendsClassName() != null) {
            ClassIdentifier classIdentifier = new ClassIdentifier(classBody.getExtendsClassName());
            ClassBody extendsClassBody = this.classDefinitions.getDefinitions().get(classIdentifier);

            getClassMethods(extendsClassBody, classBody.getExtendsClassName(), classMethods);
        }

        for (ClassMethodDeclaration classMethodDeclaration : classBody.getMethods().keySet()) {
            classMethods.put(classMethodDeclaration, className);
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

    private int _computeObjectSize(ClassBody classBody, int objectSize) {
        if (classBody.getExtendsClassName() != null) {
            ClassBody extendsClassBody = this.classDefinitions.getDefinitions().get(new ClassIdentifier(classBody.getExtendsClassName()));
            int newObjectSize = 0;
            for (ClassField classField : extendsClassBody.getFields().keySet()) {
                newObjectSize += sizeOf(classField.getType());
            }
            return _computeObjectSize(extendsClassBody, objectSize + newObjectSize);
        }
        else {
            return objectSize;
        }
    }

    private int computeObjectSize(ClassBody classBody) {
        int objectSize = 8;
        for (ClassField classField : classBody.getFields().keySet()) {
            objectSize += sizeOf(classField.getType());
        }

        return _computeObjectSize(classBody, objectSize);
    }

    private boolean isArray(String array) {
        if (array == null) {
            return false;
        }

        return array.endsWith("[]");
    }


    private String checkCurrentClass(String identifier, Argument argu) {
        ClassField classField = argu.currentClass.getValue().getFields().get(new ClassField(identifier, null));
        if (classField != null) {
            return classField.getType();
        }
        else {
            return null;
        }
    }

    private String checkParents(String identifier, String extendsClassName, ClassDefinitions classDefinitions) {
        if (extendsClassName != null) {
            ClassIdentifier temp = new ClassIdentifier(extendsClassName);
            ClassField classField = classDefinitions.getDefinitions().get(temp).getFields().get(new ClassField(identifier, null));
            if (classField != null) {
                return classField.getType();
            }
            return checkParents(identifier, classDefinitions.getDefinitions().get(temp).getExtendsClassName(), classDefinitions);
        }
        return null;
    }

    private boolean isCustomType(String type) {
        return !type.equals("int") && !type.equals("int[]") && !type.equals("boolean") && !type.equals("boolean[]");
    }

    private String getIRType(String obj) {
        return isCustomType(obj) ? "i8*" : this.types.get(obj);
    }

     /*Check if the method is contained in the subclasses of this object*/
    private ClassMethodDeclaration containsMethod(String methodIdentifier, String currentClassName, ClassDefinitions classDefinitions) {
        if (currentClassName != null) {
            ClassIdentifier classIdentifier = new ClassIdentifier(currentClassName);
            for (ClassMethodDeclaration classMethodDeclaration : classDefinitions.getDefinitions().get(classIdentifier).getMethods().keySet()) {
                if (classMethodDeclaration.equals(new ClassMethodDeclaration(methodIdentifier, null))) {
                    return classMethodDeclaration;
                }
            }

            if (classDefinitions.getDefinitions().get(classIdentifier).getExtendsClassName() != null) {
                return containsMethod(methodIdentifier, classDefinitions.getDefinitions().get(classIdentifier).getExtendsClassName(), classDefinitions);
            }
        }
        return null;
    }


    /**
     * f0 -> MainClass()
     * f1 -> ( TypeDeclaration() )*
     * f2 -> <EOF>
     */
    public Object visit(Goal n, Object argu) {
        argu = new Argument();
        n.f0.accept(this, argu);

        for (int i = 0 ; i < n.f1.size() ; i++) {
            n.f1.elementAt(i).accept(this, null);
        }

        return null;
    }


    /**
     * f0 -> "class"
     * f1 -> Identifier()
     * f2 -> "{"
     * f3 -> "public"
     * f4 -> "static"
     * f5 -> "void"
     * f6 -> "main"
     * f7 -> "("
     * f8 -> "String"
     * f9 -> "["
     * f10 -> "]"
     * f11 -> Identifier()
     * f12 -> ")"
     * f13 -> "{"
     * f14 -> ( VarDeclaration() )*
     * f15 -> ( Statement() )*
     * f16 -> "}"
     * f17 -> "}"
     */
    public Object visit(MainClass n, Object argu) {
        Argument current = (Argument) argu;
        current.performCheck = false;
        ObjectType objectType = (ObjectType ) n.f1.accept(this, current);

        ClassIdentifier classIdentifier = new ClassIdentifier(objectType.identifier);
        ClassBody classBody = this.classDefinitions.getDefinitions().get(classIdentifier);

        if (classBody == null) {
            throw new RuntimeException("This was not supposed to happen");
        }

        System.out.println("define i32 @main() {");

        current.performCheck = true;
        current.currentClass = new SimpleEntry<ClassIdentifier, ClassBody>(classIdentifier, classBody);
        current.isMethodDeclaration = true;

        if (n.f14.size() > 0) {
            System.out.println("\n\t;Allocate space for variable declarations");
        }
        for (int i = 0 ; i < n.f14.size() ; i++) {
            n.f14.elementAt(i).accept(this, current);
        }
        System.out.println();

        ClassMethodDeclaration classMethodDeclaration = new ClassMethodDeclaration("main", "void");
        for (ClassMethodDeclaration methodDeclaration : current.currentClass.getValue().getMethods().keySet()) {
            if (methodDeclaration.equals(classMethodDeclaration)) {
                classMethodDeclaration = methodDeclaration;
            }
        }
        ClassMethodBody classMethodBody = current.currentClass.getValue().getMethods().get(classMethodDeclaration);

        current.currentMethod = new SimpleEntry<ClassMethodDeclaration, ClassMethodBody>(classMethodDeclaration, classMethodBody);

        for (int i = 0 ; i < n.f15.size() ; i++) {
            n.f15.elementAt(i).accept(this, current);
        }
        System.out.println();
        System.out.println("\tret i32 0");
        System.out.println("}\n");
        return null;
    }

    /**
     * f0 -> ClassDeclaration()
     *       | ClassExtendsDeclaration()
     */
    public Object visit(TypeDeclaration n, Object argu) {
        argu = new Argument();
        return n.f0.accept(this, argu);
    }

    /**
     * f0 -> "class"
     * f1 -> Identifier()
     * f2 -> "{"
     * f3 -> ( VarDeclaration() )*
     * f4 -> ( MethodDeclaration() )*
     * f5 -> "}"
     */
    public Object visit(ClassDeclaration n, Object argu) {
        Argument current = (Argument) argu;
        current.performCheck = false;
        current.produceCode = false;
        ObjectType objectType = (ObjectType) n.f1.accept(this, current);

        ClassIdentifier classIdentifier = new ClassIdentifier(objectType.identifier);
        ClassBody classBody = this.classDefinitions.getDefinitions().get(classIdentifier);

        if (classBody == null) {
            throw new RuntimeException("This was not supposed to happen");
        }

        current.performCheck = true;

        current.currentClass = new SimpleEntry<ClassIdentifier, ClassBody>(classIdentifier, classBody);
        current.isMethodDeclaration = false;
        for (int i = 0 ; i < n.f3.size() ; i++) {
            n.f3.elementAt(i).accept(this, current);
        }

        for (int i = 0 ; i < n.f4.size() ; i++) {
            n.f4.elementAt(i).accept(this, current);
        }

        return null;
    }

    /**
     * f0 -> "class"
     * f1 -> Identifier()
     * f2 -> "extends"
     * f3 -> Identifier()
     * f4 -> "{"
     * f5 -> ( VarDeclaration() )*
     * f6 -> ( MethodDeclaration() )*
     * f7 -> "}"
     */
    public Object visit(ClassExtendsDeclaration n, Object argu) {
        Argument current = (Argument) argu;
        current.performCheck = false;
        current.produceCode = false;
        ObjectType object = (ObjectType) n.f1.accept(this, current);
        ObjectType extendsObject = (ObjectType) n.f3.accept(this, current);

        ClassIdentifier classIdentifier = new ClassIdentifier(object.identifier, extendsObject.identifier);
        ClassBody classBody = this.classDefinitions.getDefinitions().get(classIdentifier);

        if (classBody == null) {
            throw new RuntimeException("This was not supposed to happen");
        }

        current.performCheck = true;

        current.currentClass = new SimpleEntry<ClassIdentifier, ClassBody>(classIdentifier, classBody);
        current.currentMethod = null;

        current.isMethodDeclaration = false;
        for (int i = 0 ; i < n.f5.size() ; i++) {
            n.f5.elementAt(i).accept(this, current);
        }

        for (int i = 0 ; i < n.f6.size() ; i++) {
            n.f6.elementAt(i).accept(this, current);
        }

        return null;
    }

    /**
     * f0 -> "public"
     * f1 -> Type()
     * f2 -> Identifier()
     * f3 -> "("
     * f4 -> ( FormalParameterList() )?
     * f5 -> ")"
     * f6 -> "{"
     * f7 -> ( VarDeclaration() )*
     * f8 -> ( Statement() )*
     * f9 -> "return"
     * f10 -> Expression()
     * f11 -> ";"
     * f12 -> "}"
     */
    public Object visit(MethodDeclaration n, Object argu) {
        Argument current = (Argument) argu;
        current.performCheck = true;
        current.currentMethod = null;
        current.currentRegister = 0;
        ObjectType returnType = (ObjectType) n.f1.accept(this, argu);
        current.performCheck = false;
        ObjectType methodIdentifier = (ObjectType) n.f2.accept(this, argu);

        System.out.print("define " + getIRType(returnType.getType()) + " @" + current.currentClass.getKey().getClassName() + "." + methodIdentifier.identifier + "(i8* %this");

        current.performCheck = true;
        current.isMethodDeclaration = true;
        if (n.f4.present()) {
            n.f4.accept(this, argu);
        } else {
            System.out.println(") {\n");
            System.out.println("\n\t;Also allocate space for \"this\"");
            System.out.println("\t%.this = alloca i8*");
            System.out.println("\tstore i8* %this, i8** %.this");
        }

        if (n.f7.size() > 0) {
            System.out.println("\t;Allocate space for variable declarations");
        }
        for (int i = 0; i < n.f7.size(); i++) {
            n.f7.elementAt(i).accept(this, argu);
        }

        ClassMethodDeclaration classMethodDeclaration = new ClassMethodDeclaration(methodIdentifier.identifier, null);
        for (ClassMethodDeclaration methodDeclaration : current.currentClass.getValue().getMethods().keySet()) {
            if (methodDeclaration.equals(classMethodDeclaration)) {
                classMethodDeclaration = methodDeclaration;
            }
        }
        ClassMethodBody classMethodBody = current.currentClass.getValue().getMethods().get(classMethodDeclaration);

        current.currentMethod = new SimpleEntry<ClassMethodDeclaration, ClassMethodBody>(classMethodDeclaration, classMethodBody);

        for (int i = 0; i < n.f8.size(); i++) {
            n.f8.elementAt(i).accept(this, argu);
        }

        current.produceCode = true;
        ObjectType expressionReturnType = (ObjectType) n.f10.accept(this, argu);

        if (!returnType.equals(expressionReturnType)) {
            throw new RuntimeException("(line " + this.currentLine + ", column " + this.currentColumn + ") TypeError, cannot convert " + expressionReturnType.getType() + " to " + returnType.getType() + " on return expression");
        }

        System.out.println("\n\tret " + getIRType(expressionReturnType.getType()) + " %_" + expressionReturnType.returnRegister);
        System.out.println("}\n");

        return null;
    }

    /**
     * f0 -> Type()
     * f1 -> Identifier()
     * f2 -> ";"
     */
    public Object visit(VarDeclaration n, Object argu) {
        Argument current = (Argument) argu;
        current.produceCode = false;
        ObjectType objectType = (ObjectType) n.f0.accept(this, argu);

        boolean tmp = current.performCheck;
        /*Do not perform check, its just a name declaration*/
        current.performCheck = false;
        ObjectType objectIdentifier = (ObjectType) n.f1.accept(this, argu);
        current.performCheck = tmp;

        if (current.isMethodDeclaration)
            System.out.println("\t%" + objectIdentifier.identifier + " = alloca " + getIRType(objectType.getType()) + "\n");

        return objectIdentifier.identifier;
    }

    /**
     * f0 -> FormalParameter()
     * f1 -> FormalParameterTail()
     */
    public Object visit(FormalParameterList n, Object argu) {
        ((Argument) argu).produceCode = false;

        String[] returnValues = (String[]) n.f0.accept(this, argu);
        List<String[]> returnValuesList = (List<String[]>) n.f1.accept(this, argu);

        System.out.println(") {\n");

        System.out.println("\t;Allocate space for parameters");

        System.out.println(returnValues[0]);
        System.out.println(returnValues[1]);

        for (String[] values : returnValuesList) {
            System.out.println(values[0]);
            System.out.println(values[1]);
        }

        System.out.println("\n\t;Also allocate space for \"this\"");
        System.out.println("\t%.this = alloca i8*");
        System.out.println("\tstore i8* %this, i8** %.this");

        return null;
    }

    /**
     * f0 -> ( FormalParameterTerm() )*
     */
    public Object visit(FormalParameterTail n, Object argu) {
        List<String[]> returnValues = new ArrayList<String[]>();

        for (int i = 0 ; i < n.f0.size(); i++) {
            returnValues.add((String[]) n.f0.elementAt(i).accept(this, argu));
        }

        return returnValues;
    }

    /**
     * f0 -> ","
     * f1 -> FormalParameter()
     */
    public Object visit(FormalParameterTerm n, Object argu) {
        return n.f1.accept(this, argu);
    }

    /**
     * f0 -> Type()
     * f1 -> Identifier()
     */
    public Object visit(FormalParameter n, Object argu) {
        ObjectType objectType = (ObjectType) n.f0.accept(this, argu);

        Argument current = (Argument) argu;
        boolean tmp = current.performCheck;
        /*Do not perform check, its just a name declaration*/
        current.performCheck = false;
        ObjectType objectIdentifier = (ObjectType) n.f1.accept(this, argu);
        current.performCheck = tmp;

        System.out.print(", " + getIRType(objectType.getType()) + " %." + objectIdentifier.identifier);

        String[] returnValues = new String[]{
                "\t%" + objectIdentifier.identifier + " = alloca " + getIRType(objectType.getType()),
                "\tstore " + getIRType(objectType.getType()) + " %." + objectIdentifier.identifier + ", " + getIRType(objectType.getType()) + "* %" + objectIdentifier.identifier
        };

        return returnValues;
    }


    /**
     * f0 -> "{"
     * f1 -> ( Statement() )*
     * f2 -> "}"
     */
    public Object visit(Block n, Object argu) {

        for (int i = 0 ; i < n.f1.size() ; i++) {
            n.f1.elementAt(i).accept(this, argu);
        }

        this.currentLine = n.f2.beginLine;
        this.currentColumn = n.f2.beginColumn;

        return null;
    }

    /**
     * f0 -> Identifier()
     * f1 -> "="
     * f2 -> Expression()
     * f3 -> ";"
     */
    public Object visit(AssignmentStatement n, Object argu) {
        Argument current = (Argument) argu;
        current.produceCode = false;
        ObjectType identifierType = (ObjectType) n.f0.accept(this, argu);
        current.produceCode = true;
        ObjectType expressionType = (ObjectType) n.f2.accept(this, argu);

        this.currentLine = n.f1.beginLine;
        this.currentColumn = n.f1.beginColumn;

        if (!identifierType.equals(expressionType)) {
            throw new RuntimeException("(line " + this.currentLine + ", column " + this.currentColumn + ") TypeError, incompatible types, cannot convert " + expressionType.getType() + " to " + identifierType.getType());
        }
        switch (identifierType.identifierOrigin) {
            case LOCAL:
                System.out.println("\tstore " + getIRType(expressionType.getType()) + " %_" + expressionType.returnRegister + ", " + getIRType(expressionType.getType()) + "* %" + identifierType.identifier);
                break;
            case OBJECT:
                System.out.println("\n\t;Get variable from object instance\n");

                Integer identifierOffset = current.currentClass.getValue().getFieldOffsets().get(new ClassField(identifierType.identifier, null));
                if (identifierOffset == null) {
                    identifierOffset = 0;
                }

                System.out.println("\t%_" + current.currentRegister + " = getelementptr i8, i8* %this, i32 " + (identifierOffset + 8));
                current.currentRegister++;
                System.out.println("\t%_" + current.currentRegister + " = bitcast i8* %_" + (current.currentRegister - 1) + " to " + getIRType(identifierType.getType()) + "*");
                System.out.println("\tstore " + getIRType(expressionType.getType()) + " %_" + expressionType.returnRegister + ", " + getIRType(identifierType.getType()) + "* %_" + current.currentRegister);
                current.currentRegister++;
                break;
            default:
                throw new RuntimeException("This should not have happened");
        }
        System.out.println();
        return null;
    }

    /**
     * f0 -> Identifier()
     * f1 -> "["
     * f2 -> Expression()
     * f3 -> "]"
     * f4 -> "="
     * f5 -> Expression()
     * f6 -> ";"
     */
    public Object visit(ArrayAssignmentStatement n, Object argu) {
        Argument current = (Argument) argu;
        current.produceCode = false;
        ObjectType arrayType = (ObjectType) n.f0.accept(this, argu);

        if (arrayType.isPrimitive) {
            if (!isArray(arrayType.primitiveType)) {
                throw new RuntimeException("(line " + this.currentLine + ", column " + this.currentColumn + ") TypeError, " + arrayType.primitiveType + " is not an array");
            }

            current.produceCode = true;
            ObjectType accessExpressionType = (ObjectType) n.f2.accept(this, argu);
            ObjectType assignmentExpressionType = (ObjectType) n.f5.accept(this, argu);

            this.currentLine = n.f4.beginLine;
            this.currentColumn = n.f4.beginColumn;

            if (!assignmentExpressionType.isPrimitive) {
                throw new RuntimeException("(line " + this.currentLine + ", column " + this.currentColumn + ") TypeError, cannot assign " + assignmentExpressionType.getType() + " object to " + arrayType.getType());
            }

            if (!arrayType.primitiveType.substring(0, arrayType.primitiveType.length() - 2).equals(assignmentExpressionType.primitiveType)) {
                throw new RuntimeException("(line " + this.currentLine + ", column " + this.currentColumn + ") TypeError, cannot assign " + assignmentExpressionType.primitiveType + " to " + arrayType.primitiveType.substring(0, arrayType.primitiveType.length() - 2));
            }
            if (!accessExpressionType.equals("int")) {
                throw new RuntimeException("(line " + this.currentLine + ", column " + this.currentColumn + ") TypeError, index of array access should be int");
            }

            System.out.println("\t;Get array pointer");
            String arrayIRType = getIRType(arrayType.getType());
            switch(arrayType.identifierOrigin) {
                case OBJECT:
                    Integer offset = current.currentClass.getValue().getFieldOffsets().get(new ClassField(arrayType.identifier, null));
                    if (offset == null) {
                        offset = 0;
                    }

                    System.out.println("\t%_" + current.currentRegister + " = getelementptr i8, i8* %this, i32 " + (offset + 8));
                    current.currentRegister++;
                    System.out.println("\t%_" + current.currentRegister + " =  bitcast i8* %_" + (current.currentRegister - 1) + " to " + arrayIRType + "*");
                    current.currentRegister++;
                    break;
                case LOCAL:
                    System.out.println("\t%_" + current.currentRegister + " = getelementptr " + arrayIRType + ", " + arrayIRType + "* %" + arrayType.identifier + ", i32 0");
                    current.currentRegister++;
                    break;
            }

            System.out.println("\t%_" + current.currentRegister + " = load " + arrayIRType + ", " + arrayIRType + "* %_" + (current.currentRegister - 1));
            int arrayBaseRegister = current.currentRegister;
            current.currentRegister++;

            /*Check if accessExpressionType is valid index*/
            String elementType = "i32";
            if (arrayType.primitiveType.startsWith("boolean")) {
                /*If its a boolean array, it means that we need to also do a bitcast because elements are of type i8*/
                elementType = "i8";
                System.out.println("\t%_" + current.currentRegister + " = bitcast i8* %_" + arrayBaseRegister + " to i32*");
                current.currentRegister++;
                System.out.println("\t%_" + current.currentRegister + " = getelementptr i32, i32* %_" + (current.currentRegister - 1) + ", i32 -1");
            }
            else {
                System.out.println("\t%_" + current.currentRegister + " = getelementptr i32, i32* %_" + arrayBaseRegister + ", i32 -1");
            }

            current.currentRegister++;
            System.out.println("\t%_" + current.currentRegister + " = load i32, i32* %_" + (current.currentRegister - 1));
            current.currentRegister++;

            /*Check if out of bounds*/
            System.out.println("\t%_" + current.currentRegister + " = icmp ult i32 %_" + accessExpressionType.returnRegister + ", %_" + (current.currentRegister - 1));
            current.currentRegister++;
            System.out.println("\tbr i1 %_" + (current.currentRegister - 1) + ", label %oob_ok" + current.currentLabel + ", label %oob_err" + current.currentLabel);
            System.out.println();
            System.out.println("\toob_err" + current.currentLabel + ":");
            System.out.println("\tcall void @throw_oob()");
            System.out.println("\tbr label %oob_ok" + current.currentLabel);
            System.out.println();
            System.out.println("\toob_ok" + current.currentLabel + ":");

            /*OK, now access array*/
            System.out.println("\t%_" + current.currentRegister + " = getelementptr " + elementType + ", " + arrayIRType + " %_" + arrayBaseRegister + ", i32 %_" + accessExpressionType.returnRegister);
            if (elementType.equals("i8") && getIRType(assignmentExpressionType.getType()).equals("i1")) {
                /*This means that we access array of booleans, we need to convert whats returned by the array to i1*/
                current.currentRegister++;
                System.out.println("\t%_" + current.currentRegister + " = zext i1 %_" + assignmentExpressionType.returnRegister + " to i8");
                System.out.println("\tstore " + elementType + " %_" + current.currentRegister + ", " + arrayIRType + " %_" + (current.currentRegister - 1));
            }
            else {
                System.out.println("\tstore " + elementType + " %_" + assignmentExpressionType.returnRegister + ", " + arrayIRType + " %_" + current.currentRegister);
            }
            current.currentRegister++;
            current.currentLabel++;
        }
        else {
            throw new RuntimeException("(line " + this.currentLine + ", column " + this.currentColumn + ") TypeError, " + arrayType.customType.getKey() + " is not an array");
        }

        return null;
    }

    /**
     * f0 -> "if"
     * f1 -> "("
     * f2 -> Expression()
     * f3 -> ")"
     * f4 -> Statement()
     * f5 -> "else"
     * f6 -> Statement()
     */
    public Object visit(IfStatement n, Object argu) {
        Argument current = (Argument) argu;
        current.produceCode = true;

        ObjectType exprType = (ObjectType) n.f2.accept(this, argu);

        this.currentLine = n.f0.beginLine;
        this.currentColumn = n.f0.beginColumn;

        if (!exprType.equals("boolean")) {
            throw new RuntimeException("(line " + this.currentLine + ", column " + this.currentColumn + ") TypeError, non-boolean type on if statement");
        }

        int label = current.currentLabel++;

        System.out.println("\n\t;If statement\n");
        System.out.println("\tbr i1 %_" + exprType.returnRegister + ", label %if_then_" + label + ", label %if_else_" + label);

        System.out.println("\tif_then_" + label + ":");
        n.f4.accept(this, argu);
        System.out.println("\tbr label %if_end_" + label + "\n");

        System.out.println("\tif_else_" + label + ":");
        n.f6.accept(this, argu);
        System.out.println("\tbr label %if_end_" + label + "\n");

        System.out.println("\tif_end_" + label + ":");

        return null;
    }

    /**
     * f0 -> "while"
     * f1 -> "("
     * f2 -> Expression()
     * f3 -> ")"
     * f4 -> Statement()
     */
    public Object visit(WhileStatement n, Object argu) {
        Argument current = (Argument) argu;
        current.produceCode = true;

        int label_number = current.currentLabel++;
        System.out.println("\n\t;While statement\n");
        System.out.println("\tbr label %loop_again_" + label_number);
        System.out.println("\tloop_again_" + label_number + ":");

        ObjectType exprType = (ObjectType) n.f2.accept(this, argu);

        this.currentLine = n.f0.beginLine;
        this.currentColumn = n.f0.beginColumn;

        if (!exprType.equals("boolean")) {
            throw new RuntimeException("(line " + this.currentLine + ", column " + this.currentColumn + ") TypeError, non-boolean type on while statement");
        }

        System.out.println("\tbr i1 %_" + exprType.returnRegister + ", label %loop_then_" + label_number + ", label %loop_else_" + label_number + "\n");

        System.out.println("\tloop_then_" + label_number + ":");
        n.f4.accept(this, argu);
        System.out.println("\tbr label %loop_again_" + label_number);

        System.out.println("\tloop_else_" + label_number + ":");

        return null;
    }

    /**
     * f0 -> "System.out.println"
     * f1 -> "("
     * f2 -> Expression()
     * f3 -> ")"
     * f4 -> ";"
     */
    public Object visit(PrintStatement n, Object argu) {
        Argument current = (Argument) argu;
        current.produceCode = true;
        ObjectType exprType = (ObjectType) n.f2.accept(this, argu);

        this.currentLine = n.f0.beginLine;
        this.currentColumn = n.f0.beginColumn;

        if (!exprType.equals(new ObjectType("int"))) {
            throw new RuntimeException("(line " + this.currentLine + ", column " + this.currentColumn + ") TypeError, cannot convert " + exprType.getType() + " to int at print statement");
        }

        System.out.println("\t;Print number");

        System.out.println("\tcall void (i32) @print_int(i32 %_" + exprType.returnRegister + ")");

        return null;
    }

    /**
     * f0 -> Clause()
     * f1 -> "&&"
     * f2 -> Clause()
     */
    public Object visit(AndExpression n, Object argu) {
        Argument current = (Argument) argu;
        current.produceCode = true;
        ObjectType boolClauseLeft = (ObjectType) n.f0.accept(this, argu);

        this.currentLine = n.f1.beginLine;
        this.currentColumn = n.f1.beginColumn;

        if (boolClauseLeft.equals("boolean")) {
            ObjectType returnValue = new ObjectType("boolean");
            System.out.println("\n\t;Short circuiting and\n");

            int firstLabel = current.currentLabel++;
            int secondLabel = current.currentLabel++;
            int thirdLabel = current.currentLabel++;
            int fourthLabel = current.currentLabel++;

            System.out.println("\tbr i1 %_" + boolClauseLeft.returnRegister + ", label %andclause_" + secondLabel + ", label %andclause_" + firstLabel + "\n");

            System.out.println("\tandclause_" + firstLabel + ":");
            System.out.println("\tbr label %andclause_" + fourthLabel);

            System.out.println("\tandclause_" + secondLabel + ":");
            ObjectType boolClauseRight = (ObjectType) n.f2.accept(this, argu);
            if (!boolClauseRight.equals("boolean")) {
                throw new RuntimeException("(line " + this.currentLine + ", column " + this.currentColumn + ") Invalid type on binary operator && (" + boolClauseLeft.getType() + " and " + boolClauseRight.getType() + ")");
            }
            System.out.println("\tbr label %andclause_" + thirdLabel);

            System.out.println("\n\tandclause_" + thirdLabel + ":");
            System.out.println("\tbr label %andclause_" + fourthLabel + "\n");
            System.out.println("\n\tandclause_" + fourthLabel + ":");
            System.out.println("\t%_" + current.currentRegister + " = phi i1 [0, %andclause_" + firstLabel
            + "], [%_" + boolClauseRight.returnRegister + ", %andclause_" + thirdLabel + "]");

            returnValue.returnRegister = current.currentRegister++;
            return returnValue;
        }
        else {
            throw new RuntimeException("(line " + this.currentLine + ", column " + this.currentColumn + ") Invalid type on binary operator && (" + boolClauseLeft.getType() + ")");
        }
    }

    /**
     * f0 -> "("
     * f1 -> Expression()
     * f2 -> ")"
     */
    public Object visit(BracketExpression n, Object argu) {
        this.currentLine = n.f0.beginLine;
        this.currentColumn = n.f0.beginColumn;
        return n.f1.accept(this, argu);
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "<"
     * f2 -> PrimaryExpression()
     */
    public Object visit(CompareExpression n, Object argu) {
        Argument current = (Argument) argu;
        current.produceCode = true;

        ObjectType exprType1 = (ObjectType) n.f0.accept(this, argu);
        ObjectType exprType2 = (ObjectType) n.f2.accept(this, argu);

        this.currentLine = n.f1.beginLine;
        this.currentColumn = n.f1.beginColumn;

        if (exprType1.equals("int") && exprType2.equals("int")) {
            ObjectType returnValue = new ObjectType("boolean");
            System.out.println("\t%_" + current.currentRegister + " = icmp slt i32 %_" + exprType1.returnRegister + ", %_" + exprType2.returnRegister);
            returnValue.returnRegister = current.currentRegister++;
            return returnValue;
        }
        else {
            throw new RuntimeException("(line " + this.currentLine + ", column " + this.currentColumn + ") Invalid types on binary operator < (" + exprType1.getType() + " and " + exprType2.getType() + ")");
        }
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "+"
     * f2 -> PrimaryExpression()
     */
    public Object visit(PlusExpression n, Object argu) {
        Argument current = (Argument) argu;
        current.produceCode = true;

        ObjectType exprType1 = (ObjectType) n.f0.accept(this, argu);
        ObjectType exprType2 = (ObjectType) n.f2.accept(this, argu);

        this.currentLine = n.f1.beginLine;
        this.currentColumn = n.f1.beginColumn;

        if (exprType1.equals("int") && exprType2.equals("int")) {
            ObjectType returnValue = new ObjectType("int");
            System.out.println("\t%_" + current.currentRegister + " = add i32 %_" + exprType1.returnRegister + ", %_" + exprType2.returnRegister);
            returnValue.returnRegister = current.currentRegister++;
            return returnValue;
        }
        else {
            throw new RuntimeException("(line " + this.currentLine + ", column " + this.currentColumn + ") Invalid types on binary operator + (" + exprType1.getType() + " and " + exprType2.getType() + ")");
        }
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "-"
     * f2 -> PrimaryExpression()
     */
    public Object visit(MinusExpression n, Object argu) {
        Argument current = (Argument) argu;
        current.produceCode = true;

        ObjectType exprType1 = (ObjectType) n.f0.accept(this, argu);
        ObjectType exprType2 = (ObjectType) n.f2.accept(this, argu);

        this.currentLine = n.f1.beginLine;
        this.currentColumn = n.f1.beginColumn;

        if (exprType1.equals("int") && exprType2.equals("int")) {
            ObjectType returnValue = new ObjectType("int");
            System.out.println("\t%_" + current.currentRegister + " = sub i32 %_" + exprType1.returnRegister + ", %_" + exprType2.returnRegister);
            returnValue.returnRegister = current.currentRegister++;
            return returnValue;
        }
        else {
            throw new RuntimeException("(line " + this.currentLine + ", column " + this.currentColumn + ") Invalid types on binary operator - (" + exprType1.getType() + " and " + exprType2.getType() + ")");
        }
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "*"
     * f2 -> PrimaryExpression()
     */
    public Object visit(TimesExpression n, Object argu) {
        Argument current = (Argument) argu;
        current.produceCode = true;

        ObjectType exprType1 = (ObjectType) n.f0.accept(this, argu);
        ObjectType exprType2 = (ObjectType) n.f2.accept(this, argu);

        this.currentLine = n.f1.beginLine;
        this.currentColumn = n.f1.beginColumn;

        if (exprType1.equals("int") && exprType2.equals("int")) {
            ObjectType returnValue = new ObjectType("int");
            System.out.println("\t%_" + current.currentRegister + " = mul i32 %_" + exprType1.returnRegister + ", %_" + exprType2.returnRegister);
            returnValue.returnRegister = current.currentRegister++;
            return returnValue;
        }
        else {
            throw new RuntimeException("(line " + this.currentLine + ", column " + this.currentColumn + ") Invalid types on binary operator * (" + exprType1.getType() + " and " + exprType2.getType() + ")");
        }
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "["
     * f2 -> PrimaryExpression()
     * f3 -> "]"
     */
    public Object visit(ArrayLookup n, Object argu) {
        Argument current = (Argument) argu;
        current.produceCode = false;
        ObjectType arrayType = (ObjectType) n.f0.accept(this, argu);
        current.produceCode = true;
        ObjectType exprType = (ObjectType) n.f2.accept(this, argu);

        this.currentLine = n.f1.beginLine;
        this.currentColumn = n.f1.beginColumn;

        ObjectType returnValue;
        if (arrayType.isPrimitive) {
            if (isArray(arrayType.primitiveType) && exprType.equals("int")) {
                returnValue = new ObjectType(arrayType.primitiveType.substring(0, arrayType.primitiveType.length() - 2));
            }
            else if (!isArray(arrayType.primitiveType)) {
                throw new RuntimeException("(line " + this.currentLine + ", column " + this.currentColumn + ") Invalid array lookup, "  + arrayType.getType() + " is not an array");
            }
            else {
                throw new RuntimeException("(line " + this.currentLine + ", column " + this.currentColumn + ") Invalid array lookup, cannot convert " + exprType.getType() + " to " + "int");
            }

            System.out.println("\t;Get array pointer");
            String arrayIRType = getIRType(arrayType.getType());
            int arrayBaseRegister;
            if (arrayType.identifierOrigin != null) {
                /*If our primary expression for array access is identifier*/
                switch (arrayType.identifierOrigin) {
                    case OBJECT:
                        Integer offset = current.currentClass.getValue().getFieldOffsets().get(new ClassField(arrayType.identifier, null));
                        if (offset == null) {
                            offset = 0;
                        }

                        System.out.println("\t%_" + current.currentRegister + " = getelementptr i8, i8* %this, i32 " + (offset + 8));
                        current.currentRegister++;
                        System.out.println("\t%_" + current.currentRegister + " =  bitcast i8* %_" + (current.currentRegister - 1) + " to " + arrayIRType + "*");
                        current.currentRegister++;
                        break;
                    case LOCAL:
                        System.out.println("\t%_" + current.currentRegister + " = getelementptr " + arrayIRType + ", " + arrayIRType + "* %" + arrayType.identifier + ", i32 0");
                        current.currentRegister++;
                        break;
                }
                /*Load array base*/
                System.out.println("\t%_" + current.currentRegister + " = load " + arrayIRType + ", " + arrayIRType + "* %_" + (current.currentRegister - 1));
                arrayBaseRegister = current.currentRegister;
                current.currentRegister++;
            }
            else {
                /*else, it came from a complex expression and is stored on register*/
                arrayBaseRegister = arrayType.returnRegister;
            }


            /*Check if accessExpressionType is valid index*/
            String elementType = "i32";
            if (arrayType.primitiveType.startsWith("boolean")) {
                /*If its a boolean array, it means that we need to also do a bitcast because elements are of type i8*/
                elementType = "i8";
                System.out.println("\t%_" + current.currentRegister + " = bitcast i8* %_" + arrayBaseRegister + " to i32*");
                current.currentRegister++;
                System.out.println("\t%_" + current.currentRegister + " = getelementptr i32, i32* %_" + (current.currentRegister - 1) + ", i32 -1");
            }
            else {
                System.out.println("\t%_" + current.currentRegister + " = getelementptr i32, i32* %_" + arrayBaseRegister + ", i32 -1");
            }

            current.currentRegister++;
            System.out.println("\t%_" + current.currentRegister + " = load i32, i32* %_" + (current.currentRegister - 1));
            current.currentRegister++;

            /*Check if out of bounds*/
            System.out.println("\t%_" + current.currentRegister + " = icmp ult i32 %_" + exprType.returnRegister + ", %_" + (current.currentRegister - 1));
            current.currentRegister++;
            System.out.println("\tbr i1 %_" + (current.currentRegister - 1) + ", label %oob_ok" + current.currentLabel + ", label %oob_err" + current.currentLabel);
            System.out.println();
            System.out.println("\toob_err" + current.currentLabel + ":");
            System.out.println("\tcall void @throw_oob()");
            System.out.println("\tbr label %oob_ok" + current.currentLabel);
            System.out.println();
            System.out.println("\toob_ok" + current.currentLabel + ":");

            /*OK, now access array*/
            System.out.println("\t%_" + current.currentRegister + " = getelementptr " + elementType + ", " + arrayIRType + " %_" + arrayBaseRegister + ", i32 %_" + exprType.returnRegister);
            current.currentRegister++;

            System.out.println("\t%_" + current.currentRegister + " = load " + elementType + ", " + arrayIRType + " %_" + (current.currentRegister - 1));
            if (elementType.equals("i8")) {
                /*If our array is boolean array, convert the result to i1*/
                current.currentRegister++;
                System.out.println("\t%_" + current.currentRegister + " = trunc i8 %_" + (current.currentRegister - 1) + " to i1");
            }
            returnValue.returnRegister = current.currentRegister++;
            current.currentLabel++;

            return returnValue;
        }
        else {
            throw new RuntimeException("(line " + this.currentLine + ", column " + this.currentColumn + ") Invalid array lookup, " + arrayType.customType.getKey() + " is not an array");
        }
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "."
     * f2 -> "length"
     */
    public Object visit(ArrayLength n, Object argu) {
        Argument current = (Argument) argu;
        current.produceCode = true;
        ObjectType arrayType = (ObjectType) n.f0.accept(this, argu);

        this.currentLine = n.f1.beginLine;
        this.currentColumn = n.f1.beginColumn;

        ObjectType returnValue;
        if (arrayType.isPrimitive) {
            if (isArray(arrayType.primitiveType)) {
                 returnValue = new ObjectType("int");
            } else {
                throw new RuntimeException("(line " + this.currentLine + ", column " + this.currentColumn + ") Invalid length operator on non-array object");
            }

            System.out.println("\t;Get array pointer");
            String arrayIRType = getIRType(arrayType.getType());
            int arrayBaseRegister;
            if (arrayType.identifierOrigin != null) {
                /*If our primary expression for array access is identifier*/
                switch (arrayType.identifierOrigin) {
                    case OBJECT:
                        Integer offset = current.currentClass.getValue().getFieldOffsets().get(new ClassField(arrayType.identifier, null));
                        if (offset == null) {
                            offset = 0;
                        }

                        System.out.println("\t%_" + current.currentRegister + " = getelementptr i8, i8* %this, i32 " + (offset + 8));
                        current.currentRegister++;
                        System.out.println("\t%_" + current.currentRegister + " =  bitcast i8* %_" + (current.currentRegister - 1) + " to " + arrayIRType + "*");
                        current.currentRegister++;
                        break;
                    case LOCAL:
                        System.out.println("\t%_" + current.currentRegister + " = getelementptr " + arrayIRType + ", " + arrayIRType + "* %" + arrayType.identifier + ", i32 0");
                        current.currentRegister++;
                        break;
                }
                System.out.println("\t%_" + current.currentRegister + " = load " + arrayIRType + ", " + arrayIRType + "* %_" + (current.currentRegister - 1));
                arrayBaseRegister = current.currentRegister;
                current.currentRegister++;
            }
            else {
                /*else, it came from a complex expression and is stored on register*/
                arrayBaseRegister = arrayType.returnRegister;
            }

            if (arrayType.primitiveType.startsWith("boolean")) {
                /*If its a boolean array, it means that we need to also do a bitcast because elements are of type i8*/
                System.out.println("\t%_" + current.currentRegister + " = bitcast i8* %_" + arrayBaseRegister + " to i32*");
                arrayBaseRegister = current.currentRegister;
                current.currentRegister++;
            }

            System.out.println("\t%_" + current.currentRegister + " = getelementptr i32, i32* %_" + arrayBaseRegister + ", i32 -1");
            current.currentRegister++;
            System.out.println("\t%_" + current.currentRegister + " = load i32, i32* %_" + (current.currentRegister - 1));
            returnValue.returnRegister = current.currentRegister++;

            return returnValue;
        }
        else {
            throw new RuntimeException("(line " + this.currentLine + ", column " + this.currentColumn + ") TypeError, " + arrayType.customType.getKey() + " is not an array");
        }
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "."
     * f2 -> Identifier()
     * f3 -> "("
     * f4 -> ( ExpressionList() )?
     * f5 -> ")"
     */
    public Object visit(MessageSend n, Object argu) {
        Argument current = (Argument) argu;
        current.performCheck = true;

        System.out.println("\n\t;Code snippet for MessageSend\n");

        current.produceCode = true;
        ObjectType object = (ObjectType) n.f0.accept(this, argu);

        if (object.isPrimitive) {
            throw new RuntimeException("(line " + this.currentLine + ", column " + this.currentColumn + ") TypeError, cannot perform MessageSend on primitive type");
        }

        current.performCheck = false;
        ObjectType method = (ObjectType) n.f2.accept(this, argu);

        ClassMethodDeclaration classMethodDeclaration = containsMethod(method.identifier, object.getType(), classDefinitions);

        if (classMethodDeclaration == null) {
            throw new RuntimeException("(line " + this.currentLine + ", column " + this.currentColumn + ") Cannot find symbol " + method.identifier);
        }

        current.performCheck = true;

        Integer methodOffset = this.classDefinitions.getDefinitions().get(new ClassIdentifier(object.getType())).getRealOffsets().get(method.identifier);
        if (methodOffset == null) {
            methodOffset = 0;
        }

        /*Get types of parameters*/
        String methodPrototype = getIRType(classMethodDeclaration.getReturnType()) + " (i8*";
        for (MethodParameter methodParameter : classMethodDeclaration.getParameters().keySet()) {
            methodPrototype += (", " + getIRType(methodParameter.getType()));
        }
        methodPrototype += ")*";

        System.out.println("\t%_" + current.currentRegister + " = bitcast i8* %_" + object.returnRegister + " to i8***");
        current.currentRegister++;
        System.out.println("\t%_" + current.currentRegister + " = load i8**, i8*** %_" + (current.currentRegister - 1));
        current.currentRegister++;
        System.out.println("\t%_" + current.currentRegister + " = getelementptr i8*, i8** %_" + (current.currentRegister - 1) + ", i32 " + methodOffset);
        current.currentRegister++;
        System.out.println("\t%_" + current.currentRegister + " = load i8*, i8** %_" + (current.currentRegister - 1));
        current.currentRegister++;
        System.out.println("\t%_" + current.currentRegister + " = bitcast i8* %_" + (current.currentRegister - 1) + " to " + methodPrototype);
        int functionPointer = current.currentRegister++;


        System.out.println("\n\t;Make the call");

        /*We can have nested message sends.
          This means that is we call this function recursively it will set different parameters every time
          We use this tmp variable to keep our parameters from the previous call and restore them before returning
         */
        Map<MethodParameter, MethodParameter> tempParameters = current.currentParameters;
        Iterator<Map.Entry<MethodParameter, MethodParameter>> tempIterator = current.currentIterator;
        int tempCurrentParameter = current.currentParameter;

        current.currentParameters = null;
        current.currentIterator = null;

        if (n.f4.present()) {
            current.currentParameter = 0;
            current.currentIterator = classMethodDeclaration.getParameters().entrySet().iterator();
            current.currentParameters = classMethodDeclaration.getParameters();

            String expressionList = (String) n.f4.accept(this, argu);

            current.currentParameters = null;
            current.currentIterator = null;

            System.out.println("\t%_" + current.currentRegister + " = call " + getIRType(classMethodDeclaration.getReturnType()) + " %_" + functionPointer + "(i8* %_" + object.returnRegister + expressionList + ")");
        }
        else {
            if (classMethodDeclaration.getParameters().size() != 0) {
                throw new RuntimeException("(line " + this.currentLine + ", column " + this.currentColumn + ") Invalid method call, the number of arguments given (" + 0 + ") is less than expected (" + classMethodDeclaration.getParameters().size() + ")");
            }
            System.out.println("\t%_" + current.currentRegister + " = call " + getIRType(classMethodDeclaration.getReturnType()) + " %_" + functionPointer + "(i8* %_" + object.returnRegister + ")");
        }
        int returnRegister = current.currentRegister++;

        String returnType =  classMethodDeclaration.getReturnType();
        if (isCustomType(returnType)) {
            current.currentParameters = tempParameters;
            current.currentIterator = tempIterator;
            current.currentParameter = tempCurrentParameter;

            ObjectType returnObject = ObjectType.createCustomObject(null, returnType , classDefinitions, null);
            returnObject.returnRegister = returnRegister;
            return returnObject;
        }

        ObjectType returnObject = new ObjectType();

        returnObject.isPrimitive = true;
        returnObject.primitiveType = returnType;
        returnObject.returnRegister = returnRegister;

        current.currentParameters = tempParameters;
        current.currentIterator = tempIterator;
        current.currentParameter = tempCurrentParameter;

        return returnObject;
    }

    /**
     * f0 -> Expression()
     * f1 -> ExpressionTail()
     */
    public Object visit(ExpressionList n, Object argu) {
        Argument current = (Argument) argu;
        current.produceCode = true;
        ObjectType exprType = (ObjectType) n.f0.accept(this, argu);

        if (current.currentIterator.hasNext()) {
            Map.Entry<MethodParameter, MethodParameter> currentEntry = current.currentIterator.next();
            if (!exprType.equals(currentEntry.getKey())) {
                throw new RuntimeException("(line " + this.currentLine + ", column " + this.currentColumn + ") TypeError, cannot convert " + exprType.getType() + " to " + currentEntry.getKey().getType() + " on expression list");
            }
        }
        current.currentParameter++;

        String expression = ", " + getIRType(exprType.getType()) + " %_" + exprType.returnRegister;

        String expressionTail = (String) n.f1.accept(this, argu);

        if (current.currentParameter != current.currentParameters.size()) {
            throw new RuntimeException("(line " + this.currentLine + ", column " + this.currentColumn + ") Invalid method call, the number of arguments given (" + current.currentParameter + ") is less than expected (" + current.currentParameters.size() + ")");
        }

        return expressionTail.isEmpty() ? expression : expression + expressionTail;
    }

    /**
     * f0 -> ( ExpressionTerm() )*
     */
    public Object visit(ExpressionTail n, Object argu) {
        StringBuilder expressionTail = new StringBuilder();
        for (int i = 0 ; i < n.f0.size() ; i++) {
            expressionTail.append((String) n.f0.elementAt(i).accept(this, argu));
        }

        return expressionTail.toString();
    }

    /**
     * f0 -> ","
     * f1 -> Expression()
     */
    public Object visit(ExpressionTerm n, Object argu) {
        Argument current = (Argument) argu;
        ObjectType exprType = (ObjectType) n.f1.accept(this, argu);

        this.currentLine = n.f0.beginLine;
        this.currentColumn = n.f0.beginColumn;

        if (current.currentIterator.hasNext()) {
            Map.Entry<MethodParameter, MethodParameter> currentEntry = current.currentIterator.next();
            if (!exprType.equals(currentEntry.getKey())) {
                throw new RuntimeException("(line " + this.currentLine + ", column " + this.currentColumn + ") TypeError, cannot convert " + exprType.getType() + " to " + currentEntry.getKey().getType() + " on expression list");
            }
            current.currentParameter++;
        }

        return ", " + getIRType(exprType.getType()) + " %_" + exprType.returnRegister;
    }


    /**
     * f0 -> <INTEGER_LITERAL>
     */
    public Object visit(IntegerLiteral n, Object argu) {
        this.currentLine = n.f0.beginLine;
        this.currentColumn = n.f0.beginColumn;

        try {
            Integer.parseInt(n.f0.toString());
        }
        catch(NumberFormatException e) {
            throw new RuntimeException("(line " + this.currentLine + ", column " + this.currentColumn + ") " + "Failed to parse integer");
        }

        Argument current = (Argument) argu;

        ObjectType returnValue = new ObjectType("int");
        returnValue.identifier = n.f0.toString();

        System.out.println("\t%_" + current.currentRegister + " = add i32 " + returnValue.identifier + ", 0");
        returnValue.returnRegister = current.currentRegister++;

        return returnValue;
    }

    /**
     * f0 -> "true"
     */
    public Object visit(TrueLiteral n, Object argu) {
        this.currentLine = n.f0.beginLine;
        this.currentColumn = n.f0.beginColumn;

        Argument current = (Argument) argu;

        ObjectType returnValue = new ObjectType("boolean");
        returnValue.identifier = "1";

        System.out.println("\t%_" + current.currentRegister + " = add i1 1, 0");
        returnValue.returnRegister = current.currentRegister++;

        return returnValue;
    }

    /**
     * f0 -> "false"
     */
    public Object visit(FalseLiteral n, Object argu) {
        this.currentLine = n.f0.beginLine;
        this.currentColumn = n.f0.beginColumn;

        Argument current = (Argument) argu;

        ObjectType returnValue = new ObjectType("boolean");
        returnValue.identifier = "1";

        System.out.println("\t%_" + current.currentRegister + " = add i1 0, 0");
        returnValue.returnRegister = current.currentRegister++;

        return returnValue;
    }

    /**
     * f0 -> "boolean"
     * f1 -> "["
     * f2 -> "]"
     */
    public Object visit(BooleanArrayType n, Object argu) {
        this.currentLine = n.f0.beginLine;
        this.currentColumn = n.f0.beginColumn;
        return new ObjectType("boolean[]");
    }

    /**
     * f0 -> "int"
     * f1 -> "["
     * f2 -> "]"
     */
    public Object visit(IntegerArrayType n, Object argu) {
        this.currentLine = n.f0.beginLine;
        this.currentColumn = n.f0.beginColumn;
        return new ObjectType("int[]");
    }

    /**
     * f0 -> "boolean"
     */
    public Object visit(BooleanType n, Object argu) {
        this.currentLine = n.f0.beginLine;
        this.currentColumn = n.f0.beginColumn;
        return new ObjectType("boolean");
    }
    /**
     * f0 -> "int"
     */
    public Object visit(IntegerType n, Object argu) {
        this.currentLine = n.f0.beginLine;
        this.currentColumn = n.f0.beginColumn;
        return new ObjectType("int");
    }


    /**
    /**
     * f0 -> "this"
     */
    public Object visit(ThisExpression n, Object argu) {
        Argument current = (Argument) argu;
        this.currentLine = n.f0.beginLine;
        this.currentColumn = n.f0.beginColumn;

        ObjectType returnObject = ObjectType.createCustomObject("this", current.currentClass.getKey().getClassName(), classDefinitions, IdentifierOrigin.LOCAL);
        System.out.println("\t%_" + current.currentRegister + " = load i8*, i8** %.this");
        returnObject.returnRegister = current.currentRegister++;

        return returnObject;
    }

    /**
     * f0 -> "new"
     * f1 -> "boolean"
     * f2 -> "["
     * f3 -> Expression()
     * f4 -> "]"
     */
    public Object visit(BooleanArrayAllocationExpression n, Object argu) {
        Argument current = (Argument) argu;
        current.produceCode = true;
        ObjectType exprType = (ObjectType) n.f3.accept(this, argu);
        current.produceCode = false;

        this.currentLine = n.f0.beginLine;
        this.currentColumn = n.f0.beginColumn;

        if (!exprType.equals("int")) {
            throw new RuntimeException("(line " + this.currentLine + ", column " + this.currentColumn + ") TypeError, cannot convert " + exprType.getType() + " to int for array allocation");
        }

        System.out.println("\n\t;Code snippet for array allocation\n");

        ObjectType returnValue = new ObjectType("boolean[]");
        int sizeRegister = exprType.returnRegister;
        int arrayBaseRegister;

        System.out.println("\t;Since its a boolean array, we need to add 4 instead of 1 in size");
        System.out.println("\t%_" + current.currentRegister + " = add i32 %_" + sizeRegister + ", 4");
        sizeRegister = current.currentRegister;
        current.currentRegister++;
        System.out.println("\t%_" + current.currentRegister + " = icmp sge i32 %_" + (current.currentRegister - 1) + ", 4");
        current.currentRegister++;
        System.out.println("\tbr i1 %_" + (current.currentRegister - 1) + ", label %oob_ok" + current.currentLabel + ", label %oob_err" + current.currentLabel);
        System.out.println();
        System.out.println("\toob_err" + current.currentLabel + ":");
        System.out.println("\tcall void @throw_oob()");
        System.out.println("\tbr label %oob_ok" + current.currentLabel);
        System.out.println();
        System.out.println("\toob_ok" + current.currentLabel + ":");
        System.out.println("\n\t;Allocate elements on heap\n");
        System.out.println("\t%_" + current.currentRegister + " = call i8* @calloc(i32 %_" + sizeRegister + ", i32 1)");
        current.currentRegister++;
        System.out.println("\t%_" + current.currentRegister + " = bitcast i8* %_" + (current.currentRegister - 1) + " to i32*");
        arrayBaseRegister = current.currentRegister;
        current.currentRegister++;
        System.out.println("\tstore i32 %_" + (sizeRegister - 1) + ", i32* %_" + (current.currentRegister - 1));
        System.out.println("\t%_" + current.currentRegister + " = bitcast i32* %_" + arrayBaseRegister + " to i8*");
        current.currentRegister++;
        System.out.println("\t%_" + current.currentRegister + " = getelementptr i8, i8* %_" + (current.currentRegister - 1) + ", i32 4");
        current.currentLabel++;

        returnValue.returnRegister = current.currentRegister++;

        return returnValue;
    }

    /**
     * f0 -> "new"
     * f1 -> "int"
     * f2 -> "["
     * f3 -> Expression()
     * f4 -> "]"
     */
    public Object visit(IntegerArrayAllocationExpression n, Object argu) {
        Argument current = (Argument) argu;
        current.produceCode = true;
        ObjectType exprType = (ObjectType) n.f3.accept(this, argu);
        current.produceCode = false;

        this.currentLine = n.f0.beginLine;
        this.currentColumn = n.f0.beginColumn;

        if (!exprType.equals("int")) {
            throw new RuntimeException("(line " + this.currentLine + ", column " + this.currentColumn + ") TypeError, cannot convert " + exprType.getType() + " to int for array allocation");
        }

        System.out.println("\n\t;Code snippet for array allocation\n");

        ObjectType returnValue = new ObjectType("int[]");
        int sizeRegister = exprType.returnRegister;

        System.out.println("\t%_" + current.currentRegister + " = add i32 %_" + sizeRegister + ", 1");
        sizeRegister = current.currentRegister;
        current.currentRegister++;
        System.out.println("\t%_" + current.currentRegister + " = icmp sge i32 %_" + (current.currentRegister - 1) + ", 1");
        current.currentRegister++;
        System.out.println("\tbr i1 %_" + (current.currentRegister - 1) + ", label %oob_ok" + current.currentLabel + ", label %oob_err" + current.currentLabel);
        System.out.println();
        System.out.println("\toob_err" + current.currentLabel + ":");
        System.out.println("\tcall void @throw_oob()");
        System.out.println("\tbr label %oob_ok" + current.currentLabel);
        System.out.println();
        System.out.println("\toob_ok" + current.currentLabel + ":");
        System.out.println("\n\t;Allocate elements on heap\n");
        System.out.println("\t%_" + current.currentRegister + " = call i8* @calloc(i32 %_" + sizeRegister + ", i32 4)");
        current.currentRegister++;
        System.out.println("\t%_" + current.currentRegister + " = bitcast i8* %_" + (current.currentRegister - 1) + " to i32*");
        current.currentRegister++;
        System.out.println("\tstore i32 %_" + (sizeRegister - 1) + ", i32* %_" + (current.currentRegister - 1));
        current.currentRegister++;
        System.out.println("\t%_" + current.currentRegister + " = getelementptr i32, i32* %_" + (current.currentRegister - 2) + ", i32 1");
        current.currentLabel++;

        returnValue.returnRegister = current.currentRegister++;

        return returnValue;
    }

    /**
     * f0 -> "new"
     * f1 -> Identifier()
     * f2 -> "("
     * f3 -> ")"
     */
    public Object visit(AllocationExpression n, Object argu) {
        Argument current = (Argument) argu;
        SimpleEntry<ClassMethodDeclaration, ClassMethodBody> tmp = current.currentMethod;
        current.currentMethod = null;
        ObjectType ide = (ObjectType) n.f1.accept(this, argu);
        current.currentMethod = tmp;

        ObjectType returnObject = ObjectType.createCustomObject(null, ide.identifier , classDefinitions, null);

        System.out.println("\n\t;New object instance allocation\n");

        ClassBody classBody = this.classDefinitions.getDefinitions().get(new ClassIdentifier(ide.identifier));

        int objectSize = computeObjectSize(classBody);
        int methods = classBody.getRealOffsets().size();

        System.out.println("\t%_" + current.currentRegister + " = call i8* @calloc(i32 1, i32 " + objectSize + ")");
        current.currentRegister++;
        System.out.println("\t%_" + current.currentRegister + " = bitcast i8* %_" + (current.currentRegister - 1) + " to i8***");
        current.currentRegister++;
        System.out.println("\t%_" + current.currentRegister + " = getelementptr [" + methods + " x i8*], [" + methods + " x i8*]* @." + ide.identifier + "_vtable, i32 0, i32 0");
        System.out.println("\tstore i8** %_" + current.currentRegister + ", i8*** %_" + (current.currentRegister - 1));
        current.currentRegister++;

        returnObject.returnRegister = (current.currentRegister - 3);

        return returnObject;
    }


    /**
     * f0 -> "!"
     * f1 -> Clause()
     */
    public Object visit(NotExpression n, Object argu) {
        ObjectType clause = (ObjectType) n.f1.accept(this, argu);

        this.currentLine = n.f0.beginLine;
        this.currentColumn = n.f0.beginColumn;

        Argument current = (Argument) argu;
        if (clause.equals("boolean")) {
            System.out.println("\t%_" + current.currentRegister + " = xor i1 %_" + clause.returnRegister + ", 1");
            clause.returnRegister = current.currentRegister++;
            return clause;
        }
        else {
            throw new RuntimeException("(line " + this.currentLine + ", column " + this.currentColumn + ") Invalid clause on !");
        }
    }

    /**
     * f0 -> <IDENTIFIER>
    */
    public Object visit(Identifier n, Object argu) {
        Argument current = (Argument) argu;
        ObjectType objectType = new ObjectType();
        objectType.identifier = n.f0.toString();

        this.currentLine = n.f0.beginLine;
        this.currentColumn = n.f0.beginColumn;

        if (!current.performCheck) {
            objectType.isPrimitive = true;
            return objectType;
        }

        /*If current.currentMethod == null, it means that we are currently visiting
            an object(!) declaration, which means that we only need to check
            if the class of the object that we are about to declare, exists somewhere else in the code.
          Else if current.currentMethod != null, it means that we are inside a method.
            Therefore, we have a variable access and we should take into account the class we are in, the fields of the method we are in
            and parameters of this method to validate if an identifier is valid and return its type.
         */
        if (current.currentMethod == null) {
            if (!classDefinitions.getDefinitions().containsKey(new ClassIdentifier(objectType.identifier))) {
                throw new RuntimeException("(line " + this.currentLine + ", column " + this.currentColumn + ") TypeError, cannot find symbol " + objectType.identifier + " (line " + n.f0.beginLine + ", column " + n.f0.beginColumn + ")");
            } else {
                objectType.customType = new SimpleEntry<>(objectType.identifier, null);
                return objectType;
            }
        }

        /*If we reach here, it means are accessing a variable inside a method.
          Therefore, we should return its type to the caller, after verifying this variable was declared before.*/

        /*Check method fields first (shadowing)*/
        if (current.currentMethod.getValue() == null) {
            throw new RuntimeException("(line " + this.currentLine + ", column " + this.currentColumn + ") TypeError, cannot find symbol " + objectType.identifier);
        }
        String type;
        IdentifierOrigin identifierOrigin;
        MethodField methodField = current.currentMethod.getValue().getFields().get(new MethodField(objectType.identifier, null));
        if (methodField != null) {
            type = methodField.getType();
            identifierOrigin = IdentifierOrigin.LOCAL;
        } else {
            /*Check parameters of the method*/
            MethodParameter methodParameter = current.currentMethod.getKey().getParameters().get(new MethodParameter(objectType.identifier, null));
            if (methodParameter != null) {
                type = methodParameter.getType();
                identifierOrigin = IdentifierOrigin.LOCAL;
            }
            /*Finally, check if current class or its super class contains this variable*/
            else {
                if (current.currentClass.getKey().getExtendsClassName() != null) {
                    type = checkCurrentClass(objectType.identifier, current);
                    if (type == null) {
                        type = checkParents(objectType.identifier, current.currentClass.getKey().getExtendsClassName(), classDefinitions);
                        if (type == null) {
                            throw new RuntimeException("(line " + this.currentLine + ", column " + this.currentColumn + ") TypeError, cannot find symbol " + objectType.identifier);
                        }
                    }
                } else {
                    type = checkCurrentClass(objectType.identifier, current);
                    if (type == null) {
                        throw new RuntimeException("(line " + this.currentLine + ", column " + this.currentColumn + ") TypeError, cannot find symbol " + objectType.identifier);
                    }
                }
                identifierOrigin = IdentifierOrigin.OBJECT;
            }
        }

        ObjectType returnObject;
        if (isCustomType(type)) {
            returnObject = ObjectType.createCustomObject(objectType.identifier, type , classDefinitions, identifierOrigin);
        }
        else {
            objectType.isPrimitive = true;
            objectType.primitiveType = type;
            objectType.identifierOrigin = identifierOrigin;
            returnObject = objectType;
        }

        if (current.produceCode) {
            String IRType = getIRType(returnObject.getType());
            switch(returnObject.identifierOrigin) {
                case LOCAL:
                    System.out.println("\t%_" + current.currentRegister + " = load " + IRType + ", " + IRType + "* %" + objectType.identifier);
                    break;
                case OBJECT:
                    Integer offset = current.currentClass.getValue().getFieldOffsets().get(new ClassField(objectType.identifier, null));
                    if (offset == null) {
                        offset = 0;
                    }

                    System.out.println("\t%_" + current.currentRegister + " = getelementptr i8, i8* %this, i32 " + (offset + 8));
                    current.currentRegister++;
                    System.out.println("\t%_" + current.currentRegister + " = bitcast i8* %_" + (current.currentRegister - 1) + " to " + IRType + "*");
                    current.currentRegister++;
                    System.out.println("\t%_" + current.currentRegister + " = load " + IRType + ", " + IRType + "* %_" + (current.currentRegister - 1));
                    break;
            }
            returnObject.returnRegister = current.currentRegister++;
        }

        return returnObject;
    }
}