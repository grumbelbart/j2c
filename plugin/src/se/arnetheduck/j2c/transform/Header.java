package se.arnetheduck.j2c.transform;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;

public class Header {
	public static final String PUBLIC = "public:";
	public static final String PROTECTED = "public: /* protected */";
	public static final String PACKAGE = "public: /* package */";
	public static final String PRIVATE = "private:";

	private static final String i1 = TransformUtil.indent(1);

	private final ITypeBinding type;
	private final Collection<ITypeBinding> softDeps;

	private final List<IMethodBinding> constructors = new ArrayList<IMethodBinding>();
	private final Map<String, List<IMethodBinding>> methods = new TreeMap<String, List<IMethodBinding>>();
	private final List<IVariableBinding> fields = new ArrayList<IVariableBinding>();

	private String access;

	private final Transformer ctx;
	private PrintWriter pw;

	public Header(Transformer ctx, ITypeBinding type,
			Collection<ITypeBinding> softDeps) {
		this.ctx = ctx;
		this.type = type;
		this.softDeps = softDeps;
	}

	public void method(IMethodBinding mb) {
		if (mb.isConstructor()) {
			constructors.add(mb);
			return;
		}

		List<IMethodBinding> m = methods.get(mb.getName());
		if (m == null) {
			methods.put(mb.getName(), m = new ArrayList<IMethodBinding>());
		}

		m.add(mb);
	}

	public void field(IVariableBinding vb) {
		fields.add(vb);
	}

	public void write(IPath root, Collection<ITypeBinding> hardDeps,
			String body, Collection<IVariableBinding> closures,
			boolean hasInit, Collection<ITypeBinding> nested)
			throws IOException {

		String extras = getExtras(closures, hasInit, nested);

		FileOutputStream fos = new FileOutputStream(root.append(
				TransformUtil.headerName(type)).toFile());

		pw = new PrintWriter(fos);

		pw.println("// Generated from " + type.getJavaElement().getPath());
		pw.println();

		pw.println("#pragma once");
		pw.println();

		if (type.getQualifiedName().equals(String.class.getName())) {
			pw.println("#include <stddef.h>");
		}

		List<ITypeBinding> bases = TypeUtil.bases(type,
				ctx.resolve(Object.class));

		Set<String> packages = new TreeSet<String>();
		packages.add(TransformUtil.packageName(type));
		for (ITypeBinding tb : softDeps) {
			packages.add(TransformUtil.packageName(tb));
		}

		for (ITypeBinding tb : bases) {
			packages.remove(TransformUtil.packageName(tb));
		}

		boolean hasIncludes = false;

		for (String p : packages) {
			pw.println(TransformUtil.include(TransformUtil.packageHeader(p)));
			hasIncludes = true;
		}

		for (ITypeBinding dep : bases) {
			ctx.hardDep(dep);
			pw.println(TransformUtil.include(dep));
			hasIncludes = true;
		}

		for (ITypeBinding dep : hardDeps) {
			if (!bases.contains(dep)) {
				pw.println(TransformUtil.include(dep));
				hasIncludes = true;
			}
		}

		if (hasIncludes) {
			pw.println();
		}

		pw.print(type.isInterface() ? "struct " : "class ");

		pw.println(TransformUtil.qualifiedCName(type, false));

		String sep = i1 + ": public ";

		for (ITypeBinding base : bases) {
			pw.println(sep + TransformUtil.virtual(base)
					+ TransformUtil.relativeCName(base, type, true));
			sep = i1 + ", public ";
		}

		pw.println("{");

		printSuper(type);

		pw.print(body);
		pw.print(extras);

		pw.println("};");

		TransformUtil.printStringSupport(type, pw);

		pw.close();
		pw = null;
	}

	public static String initialAccess(ITypeBinding type) {
		return type.isInterface() ? PUBLIC : PRIVATE;
	}

	public static String printAccess(PrintWriter pw, IMethodBinding mb,
			String access) {
		if (mb.isConstructor() && mb.getParameterTypes().length == 0
				&& Modifier.isPrivate(mb.getModifiers())) {
			return printProtected(pw, access);
		}

		if (mb.getDeclaringClass() != null
				&& (mb.getDeclaringClass().isInterface() || mb
						.getDeclaringClass().isAnnotation())) {
			return printAccess(pw, Modifier.PUBLIC, access);
		}

		return printAccess(pw, mb.getModifiers(), access);
	}

	private static String printAccess(PrintWriter pw, IVariableBinding vb,
			String access) {
		if (vb.getDeclaringClass() != null
				&& (vb.getDeclaringClass().isInterface() || vb
						.getDeclaringClass().isAnnotation())) {
			return printAccess(pw, Modifier.PUBLIC, access);
		}

		return printAccess(pw, vb.getModifiers(), access);
	}

	public static String printAccess(PrintWriter pw, int modifiers,
			String access) {
		if (Modifier.isPrivate(modifiers)) {
			if (!PRIVATE.equals(access)) {
				access = PRIVATE;
				pw.println();
				pw.println(access);
			}
		} else if (Modifier.isProtected(modifiers)) {
			if (!PROTECTED.equals(access)) {
				access = PROTECTED;
				pw.println();
				pw.println(access);
			}
		} else if (Modifier.isPublic(modifiers)) {
			if (!PUBLIC.equals(access)) {
				access = PUBLIC;
				pw.println();
				pw.println(access);
			}
		} else {
			if (!PACKAGE.equals(access)) {
				access = PACKAGE;
				pw.println();
				pw.println(access);
			}
		}

		return access;
	}

	public static String printProtected(PrintWriter pw, String access) {
		if (!"protected:".equals(access)) {
			access = "protected:";
			pw.println(access);
		}

		return access;
	}

	private void printSuper(ITypeBinding type) {
		if (type.getSuperclass() == null) {
			return;
		}

		access = printAccess(pw, Modifier.PRIVATE, access);
		pw.format(i1 + "typedef %s super;\n",
				TransformUtil.relativeCName(type.getSuperclass(), type, true));
	}

	private void printClassLiteral() {
		access = printAccess(pw, Modifier.PUBLIC, access);
		pw.println(i1 + "static ::java::lang::Class *class_();");
	}

	/**
	 * In java, if a super class implements the method of an interface, it
	 * doesn't have to be re-implemented on the class implementing the
	 * interface. In C++ we have to forward the call to the super method - this
	 * method returns a list of methods needing such forwarding.
	 */
	public static List<IMethodBinding> baseCallMethods(ITypeBinding tb) {
		Set<IMethodBinding> im = new TreeSet<IMethodBinding>(
				new BindingComparator());

		im.addAll(TypeUtil.methods(TypeUtil.interfaces(tb)));

		List<IMethodBinding> missing = new ArrayList<IMethodBinding>(im);

		for (IMethodBinding imb : im) {
			for (IMethodBinding mb : tb.getDeclaredMethods()) {
				if (mb.isSubsignature(imb)) {
					missing.remove(imb);
					break;
				}
			}

			// Same method in two interfaces
			for (IMethodBinding mb : missing) {
				if (!mb.isEqualTo(imb) && mb.isSubsignature(imb)) {
					missing.remove(imb);
					break;
				}
			}
		}
		return missing;
	}

	public static boolean baseDeclared(Transformer ctx, ITypeBinding type,
			IMethodBinding mb) {
		return (Modifier.isAbstract(mb.getModifiers()) || type.isInterface())
				&& TransformUtil.baseHasSame(mb, type, ctx);
	}

	private String getExtras(Collection<IVariableBinding> closures,
			boolean hasInit, Collection<ITypeBinding> nested) {
		StringWriter sw = new StringWriter();
		pw = new PrintWriter(sw);

		printConstructors(closures);

		printClassLiteral();
		printClinit();
		printInit(hasInit);
		printMethods();
		printClosures(closures);
		printFields();

		printEnumMethods();
		printDtor();
		printGetClass();

		printStringOperator();

		printFriends(nested);

		pw.close();
		pw = null;
		return sw.toString();
	}

	private void printStringOperator() {
		if (TransformUtil.same(type, String.class)) {
			pw.println(i1
					+ "friend String *operator\"\" _j(const char16_t *s, size_t n);");
		}
	}

	private void printGetClass() {
		if (!type.isClass() && !type.isEnum()) {
			return;
		}

		access = printAccess(pw, Modifier.PRIVATE, access);

		pw.println(i1 + "virtual ::java::lang::Class* "
				+ TransformUtil.GET_CLASS + "();");
	}

	private void printDtor() {
		if (TransformUtil.same(type, Object.class)) {
			access = printAccess(pw, Modifier.PUBLIC, access);
			pw.println(i1 + "virtual ~Object();");
		}
	}

	/** Generate implicit enum methods */
	private void printEnumMethods() {
		if (!type.isEnum()) {
			return;
		}

		boolean hasValues = false;
		boolean hasValueOf = false;
		List<IMethodBinding> m = methods.get("values");
		if (m != null) {
			for (IMethodBinding mb : m) {
				hasValues |= isValues(mb);
			}
		}

		m = methods.get("valueOf");
		if (m != null) {
			for (IMethodBinding mb : m) {
				hasValueOf |= isValueOf(mb);
			}
		}

		for (IMethodBinding mb : type.getDeclaredMethods()) {
			if (!hasValues && isValues(mb)) {
				access = printAccess(pw, Modifier.PUBLIC, access);
				pw.print(i1);
				TransformUtil.printSignature(pw, type, mb, softDeps, false);
				pw.println(" { return nullptr; /* TODO */ }");
				hasValues = true;
			} else if (!hasValueOf && isValueOf(mb)) {
				access = printAccess(pw, Modifier.PUBLIC, access);
				pw.print(i1);
				TransformUtil.printSignature(pw, type, mb, softDeps, false);
				pw.println(" { return nullptr; /* TODO */ }");
				hasValueOf = true;
			}
		}

		return;
	}

	private boolean isValueOf(IMethodBinding mb) {
		return type.isEqualTo(mb.getReturnType())
				&& mb.getName().equals("valueOf")
				&& mb.getParameterTypes().length == 1
				&& TransformUtil.same(mb.getParameterTypes()[0], String.class);
	}

	private boolean isValues(IMethodBinding mb) {
		return type.createArrayType(1).isEqualTo(mb.getReturnType())
				&& mb.getName().equals("values")
				&& mb.getParameterTypes().length == 0;
	}

	private void printConstructors(Collection<IVariableBinding> closures) {
		if (!type.isClass() && !type.isEnum()) {
			return;
		}

		String name = TransformUtil.name(type);

		if (type.isAnonymous()) {
			assert (constructors.isEmpty());
			for (IMethodBinding mb : type.getSuperclass().getDeclaredMethods()) {
				if (TransformUtil.asBaseConstructor(mb, type)) {
					constructors.add(mb);
				}
			}
		}

		boolean hasEmpty = false;
		for (IMethodBinding mb : constructors) {
			access = printAccess(pw, mb, access);

			pw.print(i1 + name + "(");

			String sep = TransformUtil.printNestedParams(pw, type, closures);

			if (mb.getParameterTypes().length > 0) {
				pw.print(sep);
				TransformUtil.printParams(pw, type, mb, false, softDeps);
			} else {
				hasEmpty = true;
			}

			pw.println(");");
		}

		if (!hasEmpty) {
			if (constructors.size() > 0) {
				access = printProtected(pw, access);
			} else {
				access = printAccess(pw, Modifier.PUBLIC, access);
			}

			pw.print(i1 + name + "(");

			TransformUtil.printNestedParams(pw, type, closures);

			pw.println(");");

			access = printProtected(pw, access);

			pw.println(i1 + "void " + TransformUtil.CTOR + "();");
		}
	}

	private void printClinit() {
		if (type.isInterface()) {
			return;
		}

		access = printAccess(pw, Modifier.PUBLIC, access);
		pw.println(i1 + "static void " + TransformUtil.STATIC_INIT + "();");
	}

	private void printInit(boolean hasInit) {
		if (!hasInit) {
			return;
		}

		access = printAccess(pw, Modifier.PRIVATE, access);
		pw.println("void " + TransformUtil.INSTANCE_INIT + "();");
	}

	private void printMethods() {
		if (type.isClass()) {
			for (List<IMethodBinding> e : methods.values()) {
				for (IMethodBinding mb : e) {
					access = TransformUtil.declareBridge(pw, type, mb,
							softDeps, access);
				}
			}
		}

		List<IMethodBinding> superMethods = TypeUtil.methods(TypeUtil.allBases(
				type, ctx.resolve(Object.class)));
		outer: for (Iterator<IMethodBinding> i = superMethods.iterator(); i
				.hasNext();) {
			IMethodBinding supermethod = i.next();

			if (Modifier.isPrivate(supermethod.getModifiers())
					|| supermethod.isConstructor()) {
				i.remove();
				continue;
			}

			Collection<IMethodBinding> declared = methods.get(supermethod
					.getName());

			if (declared == null) {
				i.remove();
				continue;
			}

			for (IMethodBinding d : declared) {
				if (TransformUtil.sameParameters(supermethod, d, true)) {
					i.remove();
					continue outer;
				}
			}
		}

		// The remaining method need unhiding
		access = printAccess(pw, Modifier.PUBLIC, access);
		Set<String> usings = new HashSet<String>();
		for (IMethodBinding mb : superMethods) {
			String using = methodUsing(mb);
			if (using != null && !usings.contains(using)) {
				access = printAccess(pw, mb.getModifiers(), access);
				pw.println(i1 + using);
				usings.add(using);
			}
		}
	}

	public static String printSuperCalls(PrintWriter pw, Header header,
			Collection<ITypeBinding> hardDeps, String access) {
		ITypeBinding type = header.type;
		if (type.isClass()) {
			List<IMethodBinding> missing = baseCallMethods(type);
			for (IMethodBinding decl : missing) {
				IMethodBinding impl = findImpl(type, decl);
				if (impl == null) {
					// Only print super call if an implementation actually
					// exists
					assert (Modifier.isAbstract(type.getModifiers()));
					continue;
				}

				// Interface methods are always public
				access = printAccess(pw, Modifier.PUBLIC, access);

				pw.print(i1);
				TransformUtil.printSignature(pw, header.type,
						decl.getMethodDeclaration(), impl.getReturnType(),
						header.softDeps, false);
				pw.println(";");

				header.method(decl);

				if (TransformUtil.returnErased(decl)
						|| !decl.getMethodDeclaration().getReturnType()
								.getErasure()
								.isEqualTo(impl.getReturnType().getErasure())) {
					TransformUtil.addDep(impl.getReturnType(), hardDeps);
				}
			}
		}

		return access;
	}

	public static IMethodBinding findImpl(ITypeBinding type, IMethodBinding mb) {
		Collection<IMethodBinding> superMethods = TypeUtil.methods(TypeUtil
				.superClasses(type));

		for (IMethodBinding sm : superMethods) {
			// isSubsignature doesn't seem to work with generics(!)
			// if (sm.isSubsignature(mb)) {
			// return sm;
			// }

			if (sm.isConstructor()) {
				continue;
			}

			if (!sm.getName().equals(mb.getName())) {
				continue;
			}

			if (!TransformUtil.sameParameters(sm, mb, false)) {
				continue;
			}

			return sm;

		}

		return null;
	}

	private void printClosures(Collection<IVariableBinding> closures) {
		ITypeBinding sb = type.getSuperclass();
		boolean superInner = sb != null && TransformUtil.hasOuterThis(sb);
		if (TransformUtil.hasOuterThis(type)) {
			if (!superInner
					|| sb.getDeclaringClass() != null
					&& !type.getDeclaringClass().getErasure()
							.isEqualTo(sb.getDeclaringClass().getErasure())) {
				TransformUtil.addDep(type.getDeclaringClass(), softDeps);
				pw.println(i1 + TransformUtil.outerThis(type) + ";");
			}
		}

		if (closures != null) {
			for (IVariableBinding closure : closures) {
				TransformUtil.addDep(closure.getType(), softDeps);
				pw.println(i1
						+ TransformUtil.relativeCName(closure.getType(), type,
								true) + " " + TransformUtil.refName(closure)
						+ ";");
			}
		}

	}

	private void printFields() {
		for (IVariableBinding vb : fields) {
			printField(vb);
		}
	}

	private void printField(IVariableBinding vb) {
		boolean asMethod = TransformUtil.asMethod(vb);
		if (asMethod) {
			access = printAccess(pw, vb, access);
			pw.format("%sstatic %s %s&%s();\n", i1,
					TransformUtil.relativeCName(vb.getType(), type, true),
					TransformUtil.ref(vb.getType()), TransformUtil.name(vb));
		}
	}

	private void printFriends(Collection<ITypeBinding> nested) {
		for (ITypeBinding nb : nested) {
			TransformUtil.addDep(nb, softDeps);
			if (!nb.isEqualTo(type)) {
				pw.println(i1 + "friend class " + TransformUtil.name(nb) + ";");
			}
		}
	}

	private String methodUsing(IMethodBinding mb) {
		return "using "
				+ TransformUtil.relativeCName(mb.getDeclaringClass(), type,
						true) + "::" + TransformUtil.name(mb) + ";";

	}
}
