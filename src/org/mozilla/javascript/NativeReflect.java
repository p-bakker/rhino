/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript;

import org.mozilla.javascript.ScriptRuntime.StringIdOrIndex;
import org.mozilla.javascript.annotations.JSStaticFunction;

public class NativeReflect extends ScriptableObject {
	private class PropertyKey {};

    public NativeReflect() {}

    private static void checkTargetValidity(Object target) {
        if (!(target instanceof Scriptable)) throw ScriptRuntime.typeError("target must be an object");
    }

    private static boolean checkArgs(Object[] args, Class[] types, int min) {
        return checkArgs(args, types, min, min);
    }

    private static boolean checkArgs(Object[] args, Class[] types, int min, int max) {
        boolean lengthOk = args.length >= min && args.length <= max;

        if (lengthOk) {
        	// CHECKME target is always the 1st argument and can it never be a Symbol?
            for (int i = 0; i < args.length; i++) {
            	if (types[i] == null) continue;

            	if (types[i] == PropertyKey.class && (
            			args[i] instanceof NativeSymbol ||
            			args[i] instanceof String ||
            			args[i] instanceof Integer
            		)) {
            		continue;
            	}

            	if (args[i] == null) throw ScriptRuntime.typeError("null is not allowed");
                // TODO proper checking if types[i] is a PropertyKey
                if (types[i].isArray()) {
                    if (!(args[i] instanceof NativeArray)) {
                        throw ScriptRuntime.typeError("I'm not an array");
                    }
                } else if (!types[i].isInstance(args[i])) {
                    throw ScriptRuntime.typeError("arg " + i + ": " + args[i].getClass().getName() + " isn't a " + types[i].getClass().getName());
                }
            }
        }

        return lengthOk;
    }

    // TODO move this to Scriptruntime/Engine and refactor function.proto.apply to use it
    private static Object[] getValuesFromArrayOrLike(Object o) {
    	Object[] values;
    	int length;

    	// Note NativeArray.toArray changes undefined values to nulls, breaking spec-compliancy here
    	if (o instanceof NativeArray) {
    		NativeArray na = (NativeArray) o;
    		length = (int) na.getLength();
    	} else if (false) {
    		if (!(o instanceof Scriptable)) throw ScriptRuntime.typeError("");

    		Object lengthValue = ((Scriptable)o).get("length", (Scriptable)o);

    		if (lengthValue != null) {
    			length = (int)lengthValue;
    		}

        	// TODO Should support Array-like objects
    		// https://tc39.es/ecma262/#sec-createlistfromarraylike
    		Class[] elementTypes = new Class[] {
    			Undefined.class,
    			null,
    			Boolean.class,
    			String.class,
    			Symbol.class,
    			NativeNumber.class,
    			//BigInt.class,
    			Scriptable.class
    		};


    	} else {
    		length = 0;
    	}

    	Scriptable s = (Scriptable) o;

    	values = new Object[length];

		for(int i = 0; i < length; i++) {
			values[i] = s.get(i, s);
		}

    	return values;
    }

    private static Object getOwnProperty(Scriptable obj, Object key, Scriptable start)
    {
    	Object result;

    	if (key instanceof NativeSymbol) {
    		if (!(obj instanceof ScriptableObject)) throw ScriptRuntime.typeError("Object doesn't support Symbols");

    		result = ((ScriptableObject)obj).get((NativeSymbol) key, start);
    	} else if (key instanceof Integer) {
    		result = obj.get((int) key, start);
    	} else {
    		result = obj.get((String) key, start);
    	}

    	return result;
    }

    // TODO overload ScriptableObject.getProperty to allow passing the start param
    private static Object getProperty(Scriptable obj, Object key, Scriptable start)
    {
        Object result;

        do {
        	result = getOwnProperty(obj, key, start);

            if (result != Scriptable.NOT_FOUND) break;
            obj = obj.getPrototype();
        } while (obj != null);

        return result;
    }

    private static Object toPropertyKey(Context cx, Object key) {
    	if (key instanceof Symbol) {
    		return key;
    	}

        StringIdOrIndex s = ScriptRuntime.toStringIdOrIndex(key);
        return s.stringId == null ? s.index : s.stringId;
    }

    public static void init(Context cx, ScriptableObject scope, boolean sealed) {
        NativeObject r = (NativeObject) ScriptRuntime.newObjectLiteral(new Object[] {}, new Object[] {}, new int[] {}, cx, scope);

        String[] names = new String[] {
            "construct",
            "apply",
            "defineProperty",
            "getOwnPropertyDescriptor",
            "has",
            "ownKeys",
            "get",
            "set",
            "deleteProperty",
            "isExtensible",
            "preventExtensions",
            "getPrototypeOf",
            "setPrototypeOf"
        };

        r.defineFunctionProperties(names, NativeReflect.class, ScriptableObject.DONTENUM);

        ScriptableObject.defineProperty(scope, "Reflect", r, ScriptableObject.DONTENUM);
    }

    /*
     *     Reflect.construct(target, argumentsList[, newTarget])
     *     The new operator as a function. Equivalent to calling new target(...argumentsList). Also provides the option to specify a different prototype.
     */
    @JSStaticFunction
    public static Object construct(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        checkArgs(args, new Class[] {Callable.class, Object[].class, Scriptable.class}, 2, 3);

        // TODO implement
        return new Object();
    }

    /*
     *     Reflect.apply(target, thisArgument, argumentsList)
     *     Calls a target function with arguments as specified by the argumentsList parameter. See also Function.prototype.apply().
     */
    @JSStaticFunction
    public static Object apply(Context cx, Scriptable thisObj, Object[] args, Function funObj) { // FIXME argumentsList must be an Array, but Object[] crashes everything
    	// CHECKME Check out ScriptRuntime.applyOrCall and a cascade of utility functions it calls: isArrayLike, getElements, getApplyArguments etc...

    	if (!(args[1] instanceof Scriptable)) {
        	// Scriptable.call needs a Scriptable as thisObj
        	//   a change was proposed for changing the interface in https://github.com/mozilla/rhino/blob/638d09dd36ef0f2a6a60d2b53ea1a400517a9bbc/src/org/mozilla/javascript/Callable.java
        	// CHECKME Maybe box primatives?
    		throw ScriptRuntime.typeError("ECMAScript Incompatibility exception");
        }

    	checkArgs(args, new Class[] {Callable.class, Scriptable.class, Object[].class}, 3);

        Callable c = (Callable) args[0];
        Object[] ar = getValuesFromArrayOrLike(args[2]);

        return c.call(cx, thisObj, (Scriptable) args[1], ar);
}

    /*
     *     Reflect.defineProperty(target, propertyKey, attributes)
     *     Similar to Object.defineProperty(). Returns a Boolean that is true if the property was successfully defined.
     */
    @JSStaticFunction
    public static Object defineProperty(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        checkArgs(args, new Class[] {Scriptable.class, PropertyKey.class, NativeObject.class},  3);

        // TODO implement
        return new Object();
    }

    /*
     *     Reflect.getOwnPropertyDescriptor(target, propertyKey)
     *     Similar to Object.getOwnPropertyDescriptor(). Returns a property descriptor of the given property if it exists on the object,  undefined otherwise.
     */
    @JSStaticFunction
    public static Object getOwnPropertyDescriptor(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        checkArgs(args, new Class[] {Scriptable.class, PropertyKey.class}, 2);

        // TODO implement
        return new Object();
    }

    /*
     *     Reflect.has(target, propertyKey)
     *     Returns a Boolean indicating whether the target has the property. Either as own or inherited. Works like the in operator as a function.
     */
    @JSStaticFunction
    public static Object has(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
    	// CHECKME spec doesn't consider Symbol an Object, in Rhino Symbol extends ScriptableObject through
        //	primatives like string aren't allowed either, but String instances are.
        //	null & undefined aren't allowed either
        if (args[0] instanceof NativeSymbol) {
        	throw ScriptRuntime.typeError("Symbol not allowed as target");
        }

    	checkArgs(args, new Class[] {Scriptable.class,null}, 2);

        Scriptable target = (Scriptable) args[0];
        Object key = toPropertyKey(cx, args[1]);

        boolean result = false;

        do {
        	if (args[1] instanceof String) {
        		result = target.has((String)key, target);
        	} else if (args[1] instanceof Integer) {
        		result = target.has((int)key, target);
        	} else if (args[1] instanceof NativeSymbol) {
        		if (!(target instanceof ScriptableObject)) throw ScriptRuntime.typeError("Object doesn't support Symbols");

        		result = ((ScriptableObject)target).has((NativeSymbol)key, target);
        	}

            if (result == true) break;
            target = target.getPrototype();
        } while (!result && target != null);

        return result;
    }

    /*
     *     Reflect.ownKeys(target)
     *     Returns an array of the target object's own (not inherited) property keys.
     */
    @JSStaticFunction
    public static Object ownKeys(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        checkTargetValidity(args[0]);

        // CHECKME spec doesn't consider Symbol an Object, in Rhino Symbol extends ScriptableObject through
        //	primatives like string aren't allowed either, but String instances are.
        //	null & undefined aren't allowed either
        if (args[0] instanceof NativeSymbol) {
        	throw ScriptRuntime.typeError("Symbol not allowed as target");
        }

        Object[] ids = ((ScriptableObject) args[0]).getIds(true, true);
        for (int i = 0; i < ids.length; i++) {
          ids[i] = ids[i] instanceof NativeSymbol ? ids[i] : ScriptRuntime.toString(ids[i]);
        }

        return cx.newArray(thisObj, ids);
    }

    /*
     *     Reflect.get(target, propertyKey[, receiver])
     *     Returns the value of the property. Works like getting a property from an object (target[propertyKey]) as a function.
     */
    @JSStaticFunction
    public static Object get(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        if (args.length == 3 && !(args[2] instanceof Scriptable)) {
        	throw ScriptRuntime.typeError("ECMAScript incompatibility: receiver must implement Scriptable");
        }

        // CHECKME spec doesn't consider Symbol an Object, in Rhino Symbol extends ScriptableObject through
        //	primatives like string aren't allowed either, but String instances are.
        //	null & undefined aren't allowed either
        if (args[0] instanceof NativeSymbol) {
        	throw ScriptRuntime.typeError("Symbol not allowed as target");
        }

    	checkArgs(args, new Class[] {ScriptableObject.class, null, Scriptable.class}, 2, 3);

    	ScriptableObject target = (ScriptableObject) args[0];
        ScriptableObject receiver = (ScriptableObject)(args.length == 3 ? args[2] : args[0]);
        Object key = toPropertyKey(cx, args[1]);

        // CHECKME maybe put the replacement of NOT_FOUND with undefined into get Property?
        Object retval = getProperty(target, key, receiver);
        return retval == ScriptableObject.NOT_FOUND ? Undefined.instance : retval;
    }

    /*
     *     Reflect.set(target, propertyKey, value[, receiver])
     *     A function that assigns values to properties. Returns a Boolean that is true if the update was successful.
     */
    @JSStaticFunction
    public static Object set(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        checkArgs(args, new Class[] {Scriptable.class, PropertyKey.class, Object.class, Scriptable.class}, 2, 3);

        // TODO implement
        return new Object();
    }

    /*
     *     Reflect.deleteProperty(target, propertyKey)
     *     The delete operator as a function. Equivalent to calling delete target[propertyKey].
     */
    @JSStaticFunction
    public static Object deleteProperty(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        checkArgs(args, new Class[] {Scriptable.class, PropertyKey.class}, 2);

        // CHECKME spec doesn't consider Symbol an Object, in Rhino Symbol extends ScriptableObject through
        //	primitives like string aren't allowed either, but String instances are.
        //	null & undefined aren't allowed either
        if (args[0] instanceof NativeSymbol) {
        	throw ScriptRuntime.typeError("Symbol not allowed as target");
        }

        return ScriptRuntime.deleteObjectElem((Scriptable)args[0], args[1], Context.getCurrentContext());
    }

    /*
     *     Reflect.isExtensible(target)
     *     Same as Object.isExtensible(). Returns a Boolean that is true if the target is extensible.
     */
    @JSStaticFunction
    public static Boolean isExtensible(Object target) {
        checkTargetValidity(target);

        // TODO implement
        return true;
    }

    /*
     *     Reflect.preventExtensions(target)
     *     Similar to Object.preventExtensions(). Returns a Boolean that is true if the update was successful.
     */
    @JSStaticFunction
    public static Object preventExtensions(Object target) {
        checkTargetValidity(target);

        // TODO implement
        return new Object();
    }

    /*
     *     Reflect.getPrototypeOf(target)
     *     Same as Object.getPrototypeOf().
     */
    @JSStaticFunction
    public static Object getPrototypeOf(Object target) {
        checkTargetValidity(target);

        // TODO implement
        return new Object();
    }

    /*
     *     Reflect.setPrototypeOf(target, prototype)
     *     A function that sets the prototype of an object. Returns a Boolean that is true if the update was successful.
     */
    @JSStaticFunction
    public static Object setPrototypeOf(Object target, Object prototype) {
        checkTargetValidity(target);

        // TODO implement
        return new Object();
    }

    @Override
    public String getClassName() {
        return "Reflect";
    }

}
