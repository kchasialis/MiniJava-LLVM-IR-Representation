package types;

import java.util.HashMap;
import java.util.Map;

public class ClassMethodBody {
    /*Some brilliant guys in Java decided it was a good idea not to provide Set interface with a get() method
      So, we have to do something like this...*/
    private Map<MethodField, MethodField> fields;

    public ClassMethodBody() {
        this.fields = new HashMap<MethodField, MethodField>();
    }

    public void addField(MethodField field) { this.fields.put(field, field); }


    public Map<MethodField, MethodField> getFields() {
        return this.fields;
    }
}
