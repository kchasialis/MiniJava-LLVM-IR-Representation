package types;

import java.util.Optional;
import static java.util.Objects.requireNonNull;

public class ClassIdentifier {
    private final String className;
    private final Optional<String> extendsClassName;

    public ClassIdentifier(String className, String extendsClassName) {
        this.className = requireNonNull(className, "Class Name should not be null");
        this.extendsClassName = Optional.of(requireNonNull(extendsClassName, "Extends Class Name should be not null"));
    }

    public ClassIdentifier(String className) {
        this.className = requireNonNull(className, "Class Name should not be null");
        this.extendsClassName = Optional.empty();
    }

    public String getClassName() {
        return this.className;
    }

    public String getExtendsClassName() {
        if (this.extendsClassName.isPresent()) {
            return this.extendsClassName.get();
        }
        else {
            return null;
        }
    }

    public boolean equals(Object obj) {
        if (!(obj instanceof ClassIdentifier)) {
            return false;
        }
        if (obj == this) {
            return true;
        }

        ClassIdentifier rhs = (ClassIdentifier) obj;

        return this.className.equals(rhs.className);
    }

    public int hashCode() {
        return this.className.hashCode();
    }
}
