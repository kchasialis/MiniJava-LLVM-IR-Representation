package visitors;

import types.*;
import syntaxtree.*;
import visitor.GJDepthFirst;
import java.util.*;
import java.util.AbstractMap.SimpleEntry;

/**
 * Each Class Definition has:
 * 1) Class Declaration, consists of :
 * --1.1) name, extends/name
 * 2) Class Body, consists of :
 * --2.1) fields, consist of :
 * --2.1.1) type identifier
 * --2.2) methods, consists of :
 * --2.2.1) declaration (type identifier parameters)
 * --2.2.2) body (variable declarations, statements, expression)
 */


class ClassDefinitionsArgument {
    public SimpleEntry<ClassIdentifier, ClassBody> currentClass;
}


public class ClassDefinitions extends GJDepthFirst<String, ClassDefinitionsArgument> {

    /*Class identifier and body*/
    private Map<ClassIdentifier, ClassBody> definitions;
    private List<String> errorMessages;
    private int currentLine;
    private int currentColumn;

    public ClassDefinitions() {
        this.errorMessages = new ArrayList<String>();
        this.definitions = new LinkedHashMap<ClassIdentifier, ClassBody>();
        this.currentLine = 1;
        this.currentColumn = 1;
    }
    /*
    public void printOffsets() {
        Iterator<Map.Entry<ClassIdentifier, ClassBody>> iterator = definitions.entrySet().iterator();
        int count = 0;
        while (iterator.hasNext()) {
            count++;
            Map.Entry<ClassIdentifier, ClassBody> value = iterator.next();

            if (count == 1) {
                continue;
            }

            System.out.println("-----------Class " + value.getKey().getClassName() + "-----------");

            System.out.println("--Variables---");
            List<Map.Entry<ClassField, Integer>> fieldOffsets = new ArrayList<Map.Entry<ClassField, Integer>>(value.getValue().getFieldOffsets().entrySet());
            for (int i = 0 ; i < fieldOffsets.size() ; i++) {
                System.out.println(value.getKey().getClassName() + "." + fieldOffsets.get(i).getKey().getIdentifier() + " = " + fieldOffsets.get(i).getValue());
            }

            System.out.println("---Methods---");
            List<Map.Entry<String, Integer>> methodOffsets = new ArrayList<Map.Entry<String, Integer>>(value.getValue().getMethodOffsets().entrySet());
            for (int i = 0 ; i < methodOffsets.size() ; i++) {
                System.out.println(value.getKey().getClassName() + "." + methodOffsets.get(i).getKey() + " = " + methodOffsets.get(i).getValue());
            }
            System.out.println();
        }
    }*/

    /**
     * Printing definitions, for debugging purposes
     */
    public void printDefinitions() {
        Set<ClassIdentifier> classIdentifiers = definitions.keySet();

        for (ClassIdentifier classIdentifier : classIdentifiers) {
            String extendsName = classIdentifier.getExtendsClassName();
            if (extendsName != null) {
                System.out.println("Class + " + classIdentifier.getClassName() + " extends " + extendsName);
            }
            else {
                System.out.println("Class " + classIdentifier.getClassName());

            }

            ClassBody body = definitions.get(classIdentifier);

            System.out.println("Fields of class :");
            Set<ClassField> classFields = body.getFields().keySet();

            for (ClassField field : classFields) {
                System.out.println(field.getType() + " " + field.getIdentifier() + ";");
            }

            System.out.println("Methods of class :");

            Map<ClassMethodDeclaration, ClassMethodBody> methods = body.getMethods();
            Set<ClassMethodDeclaration> classMethodDeclarations = methods.keySet();

            for (ClassMethodDeclaration classMethodDeclaration : classMethodDeclarations) {
                System.out.print(classMethodDeclaration.getReturnType() + " " + classMethodDeclaration.getIdentifier() + " (");

                Set<MethodParameter> methodParameters = classMethodDeclaration.getParameters().keySet();
                int count = 0;
                for (MethodParameter methodParameter : methodParameters) {
                    System.out.print(methodParameter.getType() + " " + methodParameter.getIdentifier());
                    count++;
                    if (count != methodParameters.size()) {
                        System.out.print(", ");
                    }
                }
                System.out.print(")");

                System.out.println();

                ClassMethodBody classMethodBody = methods.get(classMethodDeclaration);
                Set<MethodField> methodFields =  classMethodBody.getFields().keySet();

                System.out.println("Fields of method : " + classMethodDeclaration.getIdentifier());
                for (MethodField methodField : methodFields) {
                    System.out.println(methodField.getType() + " " + methodField.getIdentifier() + ";");
                }
            }
            System.out.println("\n");
        }
    }

    public List<String> getErrorMessages() { return this.errorMessages; }

    public Map<ClassIdentifier, ClassBody> getDefinitions() {
        return this.definitions;
    }

    private void addFieldToClassBody(String varDeclaration, ClassBody classBody) {
        String[] tokens = varDeclaration.split("::");

        ClassField classField = new ClassField(tokens[1], tokens[0]);
        if (!classBody.getFields().containsKey(classField)) {
            classBody.addFieldOffset(classField);
            classBody.addField(classField);
        }
        else {
            errorMessages.add("(line " + this.currentLine + ", column " + this.currentColumn + ") Redeclaration of a variable in class");
        }
    }

    private boolean isIdentical(ClassMethodDeclaration lhs, ClassMethodDeclaration rhs) {
        if (lhs.getIdentifier().equals(rhs.getIdentifier())) {
            if (lhs.getReturnType().equals(rhs.getReturnType())) {

                List<MethodParameter> lhsParameters = new ArrayList<MethodParameter>(lhs.getParameters().keySet().size());
                List<MethodParameter> rhsParameters = new ArrayList<MethodParameter>(rhs.getParameters().keySet().size());
                lhsParameters.addAll(lhs.getParameters().keySet());
                rhsParameters.addAll(rhs.getParameters().keySet());

               if (lhsParameters.size() != rhsParameters.size()) {
                   return false;
               }

               for (int i = 0 ; i < lhsParameters.size() ; i++) {
                   if (!lhsParameters.get(i).getType().equals(rhsParameters.get(i).getType())) {
                       return false;
                   }
               }

               return true;
            }
            else {
                return false;
            }
        }
        else {
            return false;
        }
    }

    private int identicalMethodExists(String currentClass, ClassMethodDeclaration classMethodDeclaration) {
        if (currentClass != null) {
            ClassIdentifier temp = new ClassIdentifier(currentClass);

            Set<ClassMethodDeclaration> parentDeclarations = definitions.get(temp).getMethods().keySet();

            int retval = 0;
            if (parentDeclarations.contains(classMethodDeclaration)) {
                for (ClassMethodDeclaration parentDeclaration : parentDeclarations) {
                    boolean identical = isIdentical(classMethodDeclaration, parentDeclaration);
                    if (identical) {
                        //code 1 means that the method exists and is indeed identical
                        retval = 1;
                        break;
                    }
                }
            }
            else {
                //code 2 means that the method does not exist on any superclass, so we are free to add it
                retval = 2;
            }

            return retval == 0 ? identicalMethodExists(this.definitions.get(temp).getExtendsClassName(), classMethodDeclaration) : retval;

        }

        //code 0 means that the method exists and is not identical
        return 0;
    }

    private void addMethodToClassBody(ClassMethodDeclaration classMethodDeclaration, ClassMethodBody classMethodBody, ClassDefinitionsArgument argu) {
        if (argu.currentClass.getKey().getExtendsClassName() != null) {
            int code = identicalMethodExists(argu.currentClass.getKey().getExtendsClassName(), classMethodDeclaration);

            if (code == 1) {
                argu.currentClass.getValue().addMethod(classMethodDeclaration, classMethodBody);
            }
            else if (code == 2) {
                argu.currentClass.getValue().addMethod(classMethodDeclaration, classMethodBody);
                argu.currentClass.getValue().addMethodOffset(classMethodDeclaration);
            }
            else {
                errorMessages.add("(line " + this.currentLine + ", column " + this.currentColumn + ") Method " + classMethodDeclaration.getIdentifier() + " is also defined in a superclass with different type / parameters");
            }
        }
        else {
            argu.currentClass.getValue().addMethod(classMethodDeclaration, classMethodBody);
            argu.currentClass.getValue().addMethodOffset(classMethodDeclaration);
        }
    }

    private void addParametersToClassMethodDeclaration(String parameters, ClassMethodDeclaration classMethodDeclaration) {
        String[] parameterList = parameters.split(",");

        for (int i = 0 ; i < parameterList.length ; i++) {
            String[] tokens = parameterList[i].split("::");

            MethodParameter methodParameter = new MethodParameter(tokens[1], tokens[0]);

            if (!classMethodDeclaration.getParameters().containsKey(methodParameter)) {
                classMethodDeclaration.addToParameters(methodParameter);
            }
            else {
                errorMessages.add("(line " + this.currentLine + ", column " + this.currentColumn + ") Redeclaration of a parameter in method " + classMethodDeclaration.getIdentifier());
            }
        }
    }

    private void addFieldToClassMethodBody(String varDeclaration, ClassMethodBody classMethodBody, ClassMethodDeclaration classMethodDeclaration) {
        String[] tokens = varDeclaration.split("::");

        MethodField methodField = new MethodField(tokens[1], tokens[0]);
        if (!classMethodBody.getFields().containsKey(methodField) && !classMethodDeclaration.getParameters().containsKey(methodField)) {
            classMethodBody.addField(methodField);
        }
        else {
            errorMessages.add("(line " + this.currentLine + ", column " + this.currentColumn + ") Redeclaration of a local variable in method " + classMethodDeclaration.getIdentifier());
        }
    }


    public String visit(NodeToken n, ClassDefinitionsArgument argu) { return n.toString(); }

    /**
     * f0 -> MainClass()
     * f1 -> ( TypeDeclaration() )*
     * f2 -> <EOF>
     */
    public String visit(Goal n, ClassDefinitionsArgument argu) {
        n.f0.accept(this, argu);

        /*Accept all type declarations and fill class bodies*/
        for (int i = 0 ; i < n.f1.size() ; i++) {
            n.f1.elementAt(i).accept(this, argu);
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
    public String visit(MainClass n, ClassDefinitionsArgument argu) {
        argu = new ClassDefinitionsArgument();

        this.currentLine = n.f0.beginLine;
        this.currentColumn = n.f0.beginColumn;

        String ide = n.f1.accept(this, argu);

        ClassBody mainClassBody = new ClassBody();

        ClassIdentifier classIdentifier = new ClassIdentifier(ide);

        if (!definitions.containsKey(classIdentifier)) {
            definitions.put(classIdentifier, mainClassBody);
        }
        else {
            errorMessages.add("(line " + this.currentLine + ", column " + this.currentColumn + ") Redefinitions of main class / method");
            return null;
        }

        ClassMethodDeclaration classMethodDeclaration = new ClassMethodDeclaration("main", "void");

        ClassMethodBody classMethodBody = new ClassMethodBody();

        argu.currentClass = new SimpleEntry<ClassIdentifier, ClassBody>(classIdentifier, mainClassBody);

        for (int i = 0 ; i < n.f14.size() ; i++) {
            String varDeclaration = n.f14.elementAt(i).accept(this, argu);
            addFieldToClassMethodBody(varDeclaration, classMethodBody, classMethodDeclaration);
        }

        /*After finishing processing the entire method body, add method to our class methods*/
        addMethodToClassBody(classMethodDeclaration, classMethodBody, argu);

        return null;
    }

    /**
     * f0 -> ClassDeclaration()
     *       | ClassExtendsDeclaration()
     */
    public String visit(TypeDeclaration n, ClassDefinitionsArgument argu) {
        argu = new ClassDefinitionsArgument();
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
    public String visit(ClassDeclaration n, ClassDefinitionsArgument argu) {
        String ide = n.f1.accept(this, argu);

        ClassIdentifier classIdentifier = new ClassIdentifier(ide);
        ClassBody classBody = new ClassBody();

        if (!definitions.containsKey(classIdentifier)) {
            definitions.put(classIdentifier, classBody);
        }
        else {
            errorMessages.add("(line " + this.currentLine + ", column " + this.currentColumn + ") Redefinition of " + classIdentifier.getClassName());
            return null;
        }

        argu.currentClass = new SimpleEntry<ClassIdentifier, ClassBody>(classIdentifier, classBody);
        /*Accept all variable declarations */
        for (int i = 0 ; i < n.f3.size() ; i++) {
            String varDeclaration = n.f3.elementAt(i).accept(this, argu);
            addFieldToClassBody(varDeclaration, argu.currentClass.getValue());
        }


        /*Accept all method declarations */
        for (int i = 0 ; i < n.f4.size() ; i++) {
            n.f4.elementAt(i).accept(this, argu);
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
    public String visit(ClassExtendsDeclaration n, ClassDefinitionsArgument argu) {
        String ide = n.f1.accept(this, argu);
        String extendsIde = n.f3.accept(this, argu);

        if (!definitions.containsKey(new ClassIdentifier(extendsIde))) {
            errorMessages.add("(line " + this.currentLine + ", column " + this.currentColumn + ") Cannot find extends symbol " + extendsIde);
            return null;
        }

        ClassIdentifier classIdentifier = new ClassIdentifier(ide, extendsIde);
        ClassBody classBody = new ClassBody(extendsIde, this);

        if (!definitions.containsKey(classIdentifier)) {
            definitions.put(classIdentifier, classBody);
        }
        else {
            errorMessages.add("(line " + this.currentLine + ", column " + this.currentColumn + ") Redefinition of " + classIdentifier.getClassName());
            return null;
        }

        argu.currentClass = new SimpleEntry<ClassIdentifier, ClassBody>(classIdentifier, classBody);

        /*Accept all variable declarations */
        for (int i = 0 ; i < n.f5.size() ; i++) {
            String varDeclaration = n.f5.elementAt(i).accept(this, argu);
            addFieldToClassBody(varDeclaration, argu.currentClass.getValue());
        }

        /*Accept all method declarations */
        for (int i = 0 ; i < n.f6.size() ; i++) {
            n.f6.elementAt(i).accept(this, argu);
        }

        return null;
    }

    /**
     * f0 -> Type()
     * f1 -> Identifier()
     * f2 -> ";"
     */
    public String visit(VarDeclaration n, ClassDefinitionsArgument argu) {
        String type = n.f0.accept(this, argu);

        String ide = n.f1.accept(this, argu);

        return type + "::" + ide;
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
    public String visit(MethodDeclaration n, ClassDefinitionsArgument argu) {
        String type = n.f1.accept(this, argu);
        String ide = n.f2.accept(this, argu);

        ClassMethodDeclaration classMethodDeclaration = new ClassMethodDeclaration(ide, type);

        if (argu.currentClass.getValue().getMethods().containsKey(classMethodDeclaration)) {
            errorMessages.add("(line " + this.currentLine + ", column " + this.currentColumn + ") Redefinition of method " + classMethodDeclaration.getIdentifier() + " in class " + argu.currentClass.getKey().getClassName());
            return null;
        }

        ClassMethodBody classMethodBody = new ClassMethodBody();

        String parameters;
        if (n.f4.present()) {
            parameters = n.f4.accept(this, argu);
            if (parameters == null) {
                throw new RuntimeException("Wait, what?");
            }
            addParametersToClassMethodDeclaration(parameters, classMethodDeclaration);
        }

        /*Accept all declarations of a method inside the class*/
        for (int i = 0 ; i < n.f7.size() ; i++) {
            String varDeclaration = n.f7.elementAt(i).accept(this, argu);
            addFieldToClassMethodBody(varDeclaration, classMethodBody, classMethodDeclaration);
        }

        /*After finishing processing the entire method body, add method to our class methods*/
        addMethodToClassBody(classMethodDeclaration, classMethodBody, argu);

        return null;
    }

    /**
     * f0 -> FormalParameter()
     * f1 -> FormalParameterTail()
     */
    public String visit(FormalParameterList n, ClassDefinitionsArgument argu) {
        String firstParam = n.f0.accept(this, argu);
        String paramTail = n.f1.accept(this, argu);

        return paramTail == null ? firstParam : firstParam + paramTail;
    }

    /**
     * f0 -> Type()
     * f1 -> Identifier()
     */
    public String visit(FormalParameter n, ClassDefinitionsArgument argu) throws RuntimeException {
        String type = n.f0.accept(this, argu);
        String ide = n.f1.accept(this, argu);

        if (type != null && ide != null) {
            return type + "::" + ide;
        }
        else {
            throw new RuntimeException("Invalid syntax on parameter list");
        }
    }

    /**
     * f0 -> ( FormalParameterTerm() )*
     */
    public String visit(FormalParameterTail n, ClassDefinitionsArgument argu) {
        if (n.f0.size() == 0) {
            return null;
        }

        String retval = n.f0.elementAt(0).accept(this, argu);
        for (int i = 1 ; i < n.f0.size() ; i++) {
            retval += n.f0.elementAt(i).accept(this, argu);
        }

        return retval;
    }

    /**
     * f0 -> ","
     * f1 -> FormalParameter()
     */
    public String visit(FormalParameterTerm n, ClassDefinitionsArgument argu) {
        String retval;

        this.currentLine = n.f0.beginLine;
        this.currentColumn = n.f0.beginColumn;

        retval = n.f0.accept(this, argu);
        retval += n.f1.accept(this, argu);

        return retval;
    }

    /**
     * f0 -> ArrayType()
     *       | BooleanType()
     *       | IntegerType()
     *       | Identifier()
     */
    public String visit(Type n, ClassDefinitionsArgument argu) { return n.f0.accept(this, argu); }

    /**
     * f0 -> BooleanArrayType()
     *       | IntegerArrayType()
     */
    public String visit(ArrayType n, ClassDefinitionsArgument argu) { return n.f0.accept(this, argu); }

    /**
     * f0 -> "boolean"
     * f1 -> "["
     * f2 -> "]"
     */
    public String visit(BooleanArrayType n, ClassDefinitionsArgument argu) {
        this.currentLine = n.f0.beginLine;
        this.currentColumn = n.f0.beginColumn;
        return "boolean[]";
    }

    /**
     * f0 -> "int"
     * f1 -> "["
     * f2 -> "]"
     */
    public String visit(IntegerArrayType n, ClassDefinitionsArgument argu) {
        this.currentLine = n.f0.beginLine;
        this.currentColumn = n.f0.beginColumn;
        return "int[]";
    }

    /**
     * f0 -> "boolean"
     */
    public String visit(BooleanType n, ClassDefinitionsArgument argu) {
        this.currentLine = n.f0.beginLine;
        this.currentColumn = n.f0.beginColumn;
        return "boolean";
    }

    /**
     * f0 -> "int"
     */
    public String visit(IntegerType n, ClassDefinitionsArgument argu) {
        this.currentLine = n.f0.beginLine;
        this.currentColumn = n.f0.beginColumn;
        return "int";
    }

    /**
     * f0 -> <IDENTIFIER>
     */
    public String visit(Identifier n, ClassDefinitionsArgument argu) {
        this.currentLine = n.f0.beginLine;
        this.currentColumn = n.f0.beginColumn;
        return n.f0.toString();
    }
}
