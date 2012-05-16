package se.arnetheduck.j2c.transform;

import java.io.PrintWriter;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;

public class TypeBindingHeaderWriter {
	private final IPath root;
	private final ITypeBinding type;
	private final Transformer ctx;

	public TypeBindingHeaderWriter(IPath root, Transformer ctx,
			ITypeBinding type) {
		this.root = root;
		this.ctx = ctx;
		this.type = type;
	}

	public void write() throws Exception {
		printClass(type);

		if (!type.isInterface()) {
			StubWriter sw = new StubWriter(root, ctx, type);
			sw.write(false);
			if (TransformUtil.hasNatives(type)) {
				sw = new StubWriter(root, ctx, type);
				sw.write(true);
			}
		}
	}

	private void printClass(ITypeBinding tb) throws Exception {
		ctx.headers.add(tb);

		for (ITypeBinding nb : tb.getDeclaredTypes()) {
			printClass(nb);
		}

		PrintWriter pw = TransformUtil.openHeader(root, tb);

		List<ITypeBinding> bases = TransformUtil.getBases(tb,
				ctx.resolve(Object.class));

		for (ITypeBinding b : bases) {
			pw.println(TransformUtil.include(b));
		}

		pw.println();

		String lastAccess;

		if (type.isInterface()) {
			lastAccess = TransformUtil.PUBLIC;
			pw.print("struct ");
		} else {
			lastAccess = TransformUtil.PRIVATE;
			pw.print("class ");
		}

		pw.println(TransformUtil.qualifiedCName(tb, false));

		String sep = ": public ";
		for (ITypeBinding b : bases) {
			ctx.hardDep(b);

			pw.print(TransformUtil.indent(1));
			pw.print(sep);
			sep = ", public ";
			pw.print(TransformUtil.virtual(b));
			pw.println(TransformUtil.relativeCName(b, tb, true));
		}

		pw.println("{");

		if (tb.getSuperclass() != null) {
			pw.print(TransformUtil.indent(1));
			pw.print("typedef ");
			pw.print(TransformUtil.relativeCName(tb.getSuperclass(), tb, true));
			pw.println(" super;");
		}

		lastAccess = TransformUtil.printAccess(pw, Modifier.PUBLIC, lastAccess);

		pw.print(TransformUtil.indent(1));
		pw.println("static java::lang::Class *class_;");

		for (IVariableBinding vb : tb.getDeclaredFields()) {
			lastAccess = TransformUtil.printAccess(pw, vb.getModifiers(),
					lastAccess);
			printField(pw, vb);
		}

		pw.println();

		Set<String> usings = new HashSet<String>();

		boolean hasEmptyConstructor = false;
		boolean hasConstructor = false;
		for (IMethodBinding mb : tb.getDeclaredMethods()) {
			lastAccess = TransformUtil.printAccess(pw, mb.getModifiers(),
					lastAccess);
			printMethod(pw, tb, mb, usings);

			hasConstructor |= mb.isConstructor();
			hasEmptyConstructor |= mb.isConstructor()
					&& mb.getParameterTypes().length == 0;
		}

		if (tb.getQualifiedName().equals("java.lang.Object")) {
			pw.println("public:");
			pw.print(TransformUtil.indent(1));
			pw.println("virtual ~Object() { }");
		}

		if (!hasEmptyConstructor) {
			if (hasConstructor) {
				pw.println("protected:");
			}
			pw.print(TransformUtil.indent(1));
			pw.print(TransformUtil.name(tb));
			pw.println("() { }");
		}

		pw.println("};");

		TransformUtil.printStringSupport(tb, pw);

		pw.close();
	}

	private void printField(PrintWriter pw, IVariableBinding vb) {
		ctx.softDep(vb.getType());

		pw.print(TransformUtil.indent(1));

		Object constant = TransformUtil.constantValue(vb);
		pw.print(TransformUtil.fieldModifiers(type, vb.getModifiers(), true,
				constant != null));

		pw.print(TransformUtil.relativeCName(vb.getType(),
				vb.getDeclaringClass(), true));
		pw.print(" ");

		pw.print(TransformUtil.ref(vb.getType()));
		pw.print(vb.getName());
		pw.print("_");

		if (constant != null) {
			pw.print(" = ");
			pw.print(constant);
		}

		pw.println(";");
	}

	private void printMethod(PrintWriter pw, ITypeBinding tb,
			IMethodBinding mb, Set<String> usings) throws Exception {
		if ((Modifier.isAbstract(mb.getModifiers()) || tb.isInterface())
				&& TransformUtil.baseHasSame(mb, tb, ctx)) {
			// Defining once more will lead to virtual inheritance issues
			pw.print(TransformUtil.indent(1));
			pw.print("/*");
			TransformUtil.printSignature(pw, tb, mb, ctx, false);
			pw.println("; (already declared) */");
			return;
		}

		if (Modifier.isPrivate(mb.getModifiers())) {
			// Skip implementation details
			pw.print(TransformUtil.indent(1));
			pw.print("/*");
			TransformUtil.printSignature(pw, tb, mb, ctx, false);
			pw.println("; (private) */");
			return;
		}

		pw.print(TransformUtil.indent(1));

		TransformUtil.printSignature(pw, tb, mb, ctx, false);

		if (Modifier.isAbstract(mb.getModifiers())) {
			pw.print(" = 0");
		}

		if (mb.isConstructor()) {
			pw.print(" { _construct(");
			for (int i = 0; i < mb.getParameterTypes().length; ++i) {
				if (i > 0)
					pw.print(", ");
				pw.print("a" + i);
			}
			pw.println("); }");
			pw.print(TransformUtil.indent(1));
			pw.print("void _construct");
			TransformUtil.printParams(pw, tb, mb, ctx);
		}
		pw.println(";");

		TransformUtil.declareBridge(pw, tb, mb, ctx);

		String using = TransformUtil.methodUsing(mb);
		if (using != null) {
			if (usings.add(using)) {
				pw.print(TransformUtil.indent(1));
				pw.println(using);
			}
		}
	}
}
