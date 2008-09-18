package org.identityconnectors.contract.data.macro;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

import org.identityconnectors.common.logging.Log;
import org.junit.Assert;

public class ArrayMacro implements Macro {

	private static final Log LOG = Log.getLog(ArrayMacro.class);
	// number of mandatory parameters
	private static final int NR_OF_MANDATORY_PARAMS = 2;
	
	/**
	 * {@inheritDoc}
	 */
	public String getName() {
		return "ARRAY";
	}

	/**
	 * {@inheritDoc}
	 */
	public Object resolve(Object[] parameters) {
		
		List<Object> ret = new ArrayList<Object>();
		
		LOG.ok("enter");
		
		//number of parameters should be at least 2
		Assert.assertTrue(NR_OF_MANDATORY_PARAMS >= parameters.length);
		
		// first parameter (mandatory) is macro name
		Assert.assertEquals(parameters[0], getName());
		
		// second parameter (mandatory) is the array type
		Assert.assertTrue(parameters[1] instanceof Object);
		String inClazz = (String) parameters[1];
		
		try {
			//TODO thorough testing of this is needed
			Class clazz = Class.forName(inClazz);
			Constructor c = clazz.getConstructor(clazz);

			// the other parameters (voluntary) represent the items of the array
			for (int i = NR_OF_MANDATORY_PARAMS; i < parameters.length; i++) {
				ret.add(parameters[i]);
			}
			
			LOG.ok("''{0}'' macro was run", getName());
		} catch (Exception ex) {
			LOG.error(ex,
					"Unable to process the Array macro with class: ''{0}''",
					inClazz);
		}
		
		// in case no items are given, an empty object array is returned.
		return ret.toArray(new Object[0]);
	}

}
