/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

/*
 * Attempt to implement ES6 Proxy support
 * 
 * Issues to solve:
 * - Need to patch Rhino to allow Proxy to receive ScriptableObject in java layer: NativeJavaObject.coerceTypeImpl > case JSTYPE_JAVA_ARRAY:
 *             if (type.isInstance(value)) {
                System.out.println("OH SHIT: IT IS HAPPENING: " + value + " is an instance of " + type);
                return value;
            }
 * - JSON.stringify on an Array Proxy gives incorrect results, since the JSON impl. does a check on value instanceof NativeArray, which a Proxy isn't. Issue might be not restricted to just Arrays
 *      Can maybe be fixed by adding a toJSON property? Wouldn't that then make "'toJSON in []" return true, while it should be false?
 * - Array.isArray(new Proxy([], {})) fails since that code checks if the argument is instanceof NativeArray
 *      Can be polyfilled
 * - 'set' handler for objects with a Proxy on the prototype doesn't work, since Rhino uses 'has' to check, instead of delegating 'set'
 * - Challenge mapping enumerate and ownKeys onto getIds and getAllIds of ScriptableObject
 */

package org.mozilla.javascript;

import java.lang.reflect.Member;
import java.lang.reflect.Method;

import org.mozilla.javascript.annotations.JSConstructor;
import org.mozilla.javascript.annotations.JSStaticFunction;

public class NativeProxy extends ScriptableObject implements Wrapper/*, Function, InvocationHandler*/ {
    private static final long serialVersionUID = 4261311139328236834L;

    private boolean dummyInit = false;
    private ScriptableObject proxyTarget;
    private ScriptableObject proxyHandler;
    
    // https://tc39.es/ecma262/#sec-proxy-object-internal-methods-and-internal-slots
    private static final String GET_PROTOTYPE_OF = "getPrototypeOf";
    private static final String SET_PROTOTYPE_OF = "setPrototypeOf";
    private static final String IS_EXTENSIBLE = "isExtensible";
    private static final String PREVENT_EXTENSIONS = "preventExtensions";
    private static final String GET_OWN_PROPERTY_DESCRIPTOR = "getOwnPropertyDescriptor";
    private static final String DEFINE_PROPERTY = "defineProperty";
    private static final String HAS = "has";
    private static final String GET = "get";
    private static final String SET = "set";
    private static final String DELETE_PROPERTY = "deleteProperty";
    private static final String OWN_KEYS = "ownKeys";
    private static final String APPLY = "apply";
    private static final String CONSTRUCT = "construct";
    
    private static enum MANDATORY_PROPERTIES {
        configurable, 
        enumerable, 
        writable
    }
    
    private class Revoker implements Callable {
        private NativeProxy RevocableProxy = null;
        
        public Revoker(NativeProxy proxy){
            RevocableProxy = proxy;
        }
        
        @Override
        public Object call(Context cx, Scriptable scope, Scriptable thisObj,
                Object[] args) {
            NativeProxy p = RevocableProxy;
            if (p == null) {
                return Undefined.instance;
            }
            RevocableProxy = null;
            p.revoke();
            return Undefined.instance;
        }
    }
    
    @JSStaticFunction
    public static NativeObject revocable(ScriptableObject target, NativeObject handler) {
        NativeProxy p = new NativeProxy(target, handler);

        NativeObject retval = (NativeObject) Context.getCurrentContext().newObject(target.getParentScope());

        retval.put("proxy", retval, p);
        retval.put("revoke", retval, p.new Revoker(p));
        return retval;
    }
    
    private static Function getHandler(Scriptable target, String handlerName) {
        Object x = ScriptableObject.getProperty(target, handlerName);
        if (x == Scriptable.NOT_FOUND) {
            return null;
        }
        if (!(x instanceof Function)) {
            throw ScriptRuntime.notFunctionError(x, handlerName);
        }
        return (Function) x;
    }
    
    private Object callHandler(Function handler, Object[] args) {
        return handler.call(Context.getCurrentContext(), this.proxyHandler, this.proxyHandler, args);
    }

    public static void init(Context cx, ScriptableObject scope, boolean sealed) {
        // CHECKME would be nicer using a Proxy instance and not a ScriptableObject instance
        //        but the getClassName method of the prototype passed to ctor.addAsConstructor(scope, p); must return 'Proxy'
        //        and Proxy.getClassName reflects the ProxyTarget. Could use the dummyInit flag though
        //Proxy p = new Proxy();
        
        Scriptable p = new ScriptableObject() {

            @Override
            public String getClassName() {
                return "Proxy";
            }};

        //p.setPrototype(getFunctionPrototype(scope));
        //p.setParentScope(scope);    
        
        try {
            // CHECKME tries using "Proxy as name for the FunctionObject", but that broken everything
            // In Chrome console.log(Proxy) shows a function named Proxy
            Member ctorMember = NativeProxy.class.getMethod("constructor", new Class[] {Context.class, Object[].class, Function.class, Boolean.TYPE});
            FunctionObject ctor = new FunctionObject("constructor", ctorMember, scope);
           
            ctor.addAsConstructor(scope, p);
            
            Member revocableMember = NativeProxy.class.getMethod("revocable", new Class[] {ScriptableObject.class, NativeObject.class});
            FunctionObject revocable = new FunctionObject("revocable", revocableMember, scope);
            
            defineProperty(ctor, "revocable", revocable, DONTENUM);
        } catch (NoSuchMethodException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (SecurityException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
    
    @JSConstructor
    public static NativeProxy constructor(Context cx, Object[] args, Function ctorObj, boolean inNewExpr) {
        if (!inNewExpr) throw ScriptRuntime.typeError("Proxy invoked without new keyword");
        
        // TODO validate args: length, values
        
        if (!(args[0] instanceof NativeObject)) {
            throw ScriptRuntime.typeError("Target not a NativeObject");
        }
        if (!(args[1] instanceof NativeObject)) {
            throw ScriptRuntime.typeError("Handler not a NativeObject");
        }
        
        NativeProxy p = new NativeProxy(args[0], args[1]);
        
        // TODO prototype should be a function I think, according to the specs
        //p.setPrototype()
        return p;
    }

    public NativeProxy() {
        this.dummyInit = true;
    }
    
    // TODO target ought to be of type Scriptable, so it also covers scopes which could be passed as target
    //        however: in Rhino its ScriptableObject that defines stuff like .isExtendible/.preventExtentions or .defineProperty
    public NativeProxy(Object target, Object handler) { //TODO should probably be varargs, so proper TypeError can be thrown when not supplying the correct number of params
        if (target == null || handler == null) {
            throw ScriptRuntime.typeError("null is not a non-null Object");
        }
        
        if (!(target instanceof ScriptableObject) ||
            !(handler instanceof ScriptableObject) || 
            ! (ScriptableObject.getObjectPrototype(((ScriptableObject)target).getParentScope())).hasInstance((Scriptable)handler)) {
            throw ScriptRuntime.typeError("Cannot create proxy with a non-object as target or handler");
        }

        //if (!(handler instanceof ScriptableObject) || ((ScriptableObject)handler).hasInstance(NativeObject.getObjectPrototype(getParentScope()))) {
        
        this.proxyTarget = (ScriptableObject) target;
        this.proxyHandler = (ScriptableObject) handler;
        
        
        //super.setPrototype(TopLevel.getBuiltinPrototype(((ScriptableObject)target).getParentScope(), TopLevel.Builtins.Function));
        //super.setPrototype(new NativeObject());
    }
    
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getName().startsWith("add")) {
            return false;
        }
        return method.invoke(this, args);
    }
    
    //EMD: Half-ass attempt to get a dymanic constructor going....
    
    protected void revoke() {
        this.proxyTarget = null;
        this.proxyHandler = null;
    }

    private void testRevoked() {
        if (!this.dummyInit && (this.proxyTarget == null || this.proxyHandler == null)) {
            throw ScriptRuntime.typeError("Proxy is revoked");
        }
    }

    @Override
    public void put(String name, Scriptable start, Object value) {
        testRevoked();
        Function f = getHandler(this.proxyHandler, SET);
        if (f == null) {
            this.proxyTarget.put(name, start, value);
        } else {
            callHandler(f, new Object[] {this.proxyTarget, name, value, start});
        }
    }
    
    @Override
    public void put(int index, Scriptable start, Object value) {
        testRevoked();
        Function f = getHandler(this.proxyHandler, SET);
        if (f == null) {
            this.proxyTarget.put(index, start, value);
        } else {
            callHandler(f, new Object[] {this.proxyTarget, index, value, start});
        }
    }
    
    @Override
    public Object get(int index, Scriptable start) {
        testRevoked();
        Function f = getHandler(this.proxyHandler, GET);
        if (f == null) {
            return this.proxyTarget.get(index, start);
        }
        return callHandler(f, new Object[] {this.proxyTarget, index, start});
    }

    @Override
    public Object get(String name, Scriptable start) {
        testRevoked();
        Function f = getHandler(this.proxyHandler, GET);
        if (f == null) {
            return this.proxyTarget.get(name, start);
        }
        return callHandler(f, new Object[] {this.proxyTarget, name, start});
    }
    
    @Override
    public boolean has(int index, Scriptable start) {
        testRevoked();
        Function f = getHandler(this.proxyHandler, HAS);
        if (f == null) {
            return this.proxyTarget.has(index, start);
        }
        Object retval =  callHandler(f, new Object[] {this.proxyTarget, index, start}); // CHECKME should start be passed in?
        if (retval instanceof Boolean) {
            return (boolean) retval;
        }
        return false; //This correct?
    }
    
    @Override
    public boolean has(String name, Scriptable start) {
        testRevoked();
        Function f = getHandler(this.proxyHandler, HAS);
        if (f == null) {
            return this.proxyTarget.has(name, start);
        }
        Object retval =  callHandler(f, new Object[] {this.proxyTarget, name, start}); // CHECKME should start be passed in?
        if (retval instanceof Boolean) {
            return (boolean) retval;
        }
        return false; //This correct?
    }
    
    @Override
    public void delete(int index) {
        testRevoked();
        Function f = getHandler(this.proxyHandler, DELETE_PROPERTY);
        if (f == null) {
            this.proxyTarget.delete(index);
        } else {
            callHandler(f, new Object[] {this.proxyTarget, index});
        }
    }
    
    @Override
    public void delete(String name) {
        testRevoked();
        Function f = getHandler(this.proxyHandler, DELETE_PROPERTY);
        if (f == null) {
            this.proxyTarget.delete(name);
        } else {
            callHandler(f, new Object[] {this.proxyTarget, name});
        }
    }
    
    // CHECKME need to have both getIds and getAllIds?
    @Override
    public Object[] getIds() {
        testRevoked();
        Function handler = getHandler(this.proxyHandler, OWN_KEYS);
        if (handler == null) {
            return this.proxyTarget.getIds();
        }

        NativeArray keys = (NativeArray) callHandler(handler, new Object[] {this.proxyTarget});
        return keys.toArray();
    }
    
    @Override
    public Object[] getAllIds() {
        testRevoked();
        Function f = getHandler(this.proxyHandler, OWN_KEYS);
        if (f == null) {
            return this.proxyTarget.getAllIds();
        }
        NativeArray keys = (NativeArray) callHandler(f, new Object[] {this.proxyTarget});
        return keys.toArray();
    }

//    @Override
//    public Scriptable getPrototype() {
//        testRevoked();
//        Function f = getHandler(this.proxyHandler, GET_PROTOTYPE_OF);
//        if (f == null) {
//            return this.proxyTarget.getPrototype();
//        }
//        return (Scriptable) callHandler(f, new Object[] {this.proxyTarget});
//    }
    
    @Override
    public void setPrototype(Scriptable m) {
        super.setPrototype(m);
//        //testRevoked();
//        Function f = getHandler(this.proxyHandler, SET_PROTOTYPE_OF);
//        if (f == null) {
//            this.proxyTarget.setPrototype(m);
//        }
//        callHandler(f, new Object[] {this.proxyTarget, m});
    }
    
    @Override
    public void defineOwnProperty(Context cx, Object id, ScriptableObject desc) {
        testRevoked();
        Function f = getHandler(this.proxyHandler, DEFINE_PROPERTY);
        if (f == null) {
            this.proxyTarget.defineOwnProperty(cx, id, desc);
        }
        
        callHandler(f, new Object[] {this.proxyTarget, id, desc});
    }
    
    @Override
    protected ScriptableObject getOwnPropertyDescriptor(Context cx, Object id) {
        testRevoked();
        Function f = getHandler(this.proxyHandler, GET_OWN_PROPERTY_DESCRIPTOR);
        if (f == null) {
            //workaround for not being able to call the protected method getOwnPropertyDescriptor on this.proxyTarget directly
            return (ScriptableObject) ScriptableObject.callMethod(cx, this, GET_OWN_PROPERTY_DESCRIPTOR, new Object[] {this.proxyTarget, id});
        }
        
        //TODO: handle invariants: https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Proxy/handler/getOwnPropertyDescriptor#Invariants
        ScriptableObject proxiedDescriptor = (ScriptableObject) callHandler(f, new Object[] {this.proxyTarget, id});
        
        //ScriptableObject realDescriptor = this.proxyTarget.getOwnPropertyDescriptor(cx, id);
        
//        if (realDescriptor.get("configurable") != proxiedDescriptor.get("configurable")) {
//            throw ScriptRuntime.typeError("...");
//        }
        
        //Check existence of mandatory properties configurable, enumerable, writable
        if (proxiedDescriptor != null) {
            for (NativeProxy.MANDATORY_PROPERTIES prop : NativeProxy.MANDATORY_PROPERTIES.values()) {
                if (!ScriptableObject.hasProperty(proxiedDescriptor, prop.name())) {
                    ScriptableObject.putProperty(proxiedDescriptor, prop.name(), false);
                }
            }
        }
        
        return proxiedDescriptor;
    }

    @Override
    public boolean isExtensible() {
        testRevoked();
        Function f = getHandler(this.proxyHandler, IS_EXTENSIBLE);
        if (f == null) {
            return this.proxyTarget.isExtensible();
        }
        return (boolean) callHandler(f, new Object[] {this.proxyTarget});
    }
    
    @Override
    public void preventExtensions() {
        testRevoked();
        Function f = getHandler(this.proxyHandler, PREVENT_EXTENSIONS);
        if (f == null) {
            this.proxyTarget.preventExtensions();
        } else {
            callHandler(f, new Object[] {this.proxyTarget});
        }
    }

//    @Override
//    public Object call(Context cx, Scriptable scope, Scriptable thisObj,
//            Object[] args) {
//        if (!(this.proxyTarget instanceof Function)) {
//            throw ScriptRuntime.notFunctionError(this, "not a function"); //CHECKME does this generate A proper error?
//        }
//        
//        Function handler = getHandler(this.proxyHandler, APPLY);
//        if (handler == null) {
//            Function f = (Function) this.proxyTarget;
//            return f.call(cx, scope, thisObj, args);
//        } else {
//            return callHandler(handler, new Object[] {this.proxyTarget, thisObj, cx.newArray(scope, args)}); //CHECKME correct params?
//        }
//    }

//    @Override
//    public Scriptable construct(Context cx, Scriptable scope, Object[] args) {
//        if (!(this.proxyTarget instanceof Function)) {
//            throw ScriptRuntime.notFunctionError(this, "not a function"); //CHECKME does this generate A proper error?
//        }
//        
//        Function handler = getHandler(this.proxyHandler, CONSTRUCT);
//        if (handler == null) {
//            Function f = (Function) this.proxyTarget;
//            return f.construct(cx, scope, args);
//        } else {
//            return (Scriptable) callHandler(handler, new Object[] {this.proxyTarget, cx.newArray(scope, args)}); //CHECKME correct params?
//        }
//    }
    
    @Override
    public boolean hasInstance(Scriptable instance) {
        testRevoked();
        return this.proxyTarget.hasInstance(instance);
    }
    
    @Override
    public String getClassName() {
        return this.proxyTarget != null ? this.proxyTarget.getClassName() : "Proxy";
    }
    
    @Override
    public Object getDefaultValue(Class<?> typeHint) {
        //In the Variables View in the debugger Proxy instances show as [Object object], whereas NativeObject instances show as "Object"
        return this.proxyTarget.toString(); // CHECKME should this not call this.proxyTarget.getDefaultValue(typeHint);?
    }

    @Override
    public Object unwrap() {
        return this.proxyTarget instanceof Wrapper ? ((Wrapper)this.proxyTarget).unwrap() : this.proxyTarget;
    }
}