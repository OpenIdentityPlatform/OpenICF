package org.identityconnectors.framework.impl.api.local.operations;

import org.identityconnectors.common.Assertions;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ResultsHandler;

public class NormalizingResultsHandler implements ResultsHandler {
    
    private final ResultsHandler _target;
    private final ObjectNormalizerFacade _normalizer;
    
    public NormalizingResultsHandler(ResultsHandler target,
            ObjectNormalizerFacade normalizer) {
        Assertions.nullCheck(target, "target");
        Assertions.nullCheck(normalizer, "normalizer");
        _target = target;
        _normalizer = normalizer;
    }
    

    public boolean handle(ConnectorObject obj) {
        ConnectorObject normalized = _normalizer.normalizeObject(obj);
        return _target.handle(normalized);
    }

}
