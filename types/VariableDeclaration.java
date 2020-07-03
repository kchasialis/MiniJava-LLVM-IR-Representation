package types;

public class VariableDeclaration {
    private final String identifier;
    private final String type;

    public VariableDeclaration(String identifier, String type) {
        this.identifier = identifier;
        this.type = type;
    }

    public String getType() {
        return this.type;
    }

    public String getIdentifier() {
        return this.identifier;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof VariableDeclaration)) {
            return false;
        }
        if (obj == this) {
            return true;
        }

        VariableDeclaration rhs = (VariableDeclaration) obj;

        return this.getIdentifier().equals(rhs.getIdentifier());
    }

    @Override
    public int hashCode() {
        return this.getIdentifier().hashCode();
    }
}
