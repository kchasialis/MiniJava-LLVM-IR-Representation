package types;

import java.util.LinkedHashMap;
import java.util.Map;

public class ClassMethodDeclaration {
    private final String identifier;
    private final String typeValue;
    /*Some brilliant guys in Java decided it was a good idea not to provide Set interface with a get() method
      So, we have to do something like this...*/
    private Map<MethodParameter, MethodParameter> parameters;

    public ClassMethodDeclaration(String identifier, String typeValue) {
        this.identifier = identifier;
        this.typeValue = typeValue;
        this.parameters = new LinkedHashMap<MethodParameter, MethodParameter>();
    }

    public String getReturnType() {
        return this.typeValue;
    }

    public String getIdentifier() {
        return this.identifier;
    }

    public Map<MethodParameter, MethodParameter> getParameters() {
        return this.parameters;
    }

    public void addToParameters(MethodParameter parameter) {
        this.parameters.put(parameter, parameter);
    }

    public boolean equals(Object obj) {
        if (!(obj instanceof ClassMethodDeclaration)) {
            return false;
        }
        if (obj == this) {
            return true;
        }

        ClassMethodDeclaration rhs = (ClassMethodDeclaration) obj;

        return this.identifier.equals(rhs.identifier);
    }

    public int hashCode() {
        return this.identifier.hashCode();
    }
}
