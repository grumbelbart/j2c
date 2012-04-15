package se.arnetheduck.j2c.transform;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;

public class StubWriter {
	private final IPath root;
	private final ITypeBinding type;
	private final Transformer ctx;
	private Set<ITypeBinding> hardDeps = new TreeSet<ITypeBinding>(
			new Transformer.TypeBindingComparator());

	private PrintWriter pw;

	public StubWriter(IPath root, Transformer ctx, ITypeBinding type) {
		if (type.isInterface()) {
			throw new UnsupportedOperationException();
		}

		this.root = root;
		this.ctx = ctx;
		this.type = type;
	}

	protected void hardDep(ITypeBinding dep) {
		TransformUtil.addDep(dep, hardDeps);
		ctx.hardDep(dep);
	}

	public void write(boolean natives) throws Exception {
		if (natives) {
			ctx.natives.add(type);
			pw = TransformUtil.openImpl(root, type, TransformUtil.NATIVE);
		} else {
			ctx.stubs.add(type);
			pw = TransformUtil.openImpl(root, type, TransformUtil.STUB);
		}

		for (ITypeBinding tb : hardDeps) {
			pw.println(TransformUtil.include(tb));
		}

		pw.print(body(natives));

		pw.close();
	}

	public String body(boolean natives) throws Exception {
		PrintWriter old = pw;

		StringWriter ret = new StringWriter();
		pw = new PrintWriter(ret);

		for (IVariableBinding vb : type.getDeclaredFields()) {
			printField(vb);
		}

		pw.println();

		for (IMethodBinding mb : type.getDeclaredMethods()) {
			if (Modifier.isNative(mb.getModifiers()) == natives) {
				printMethod(type, mb);
			}
		}

		pw.close();

		pw = old;

		return ret.toString();
	}

	private void printField(IVariableBinding vb) {
		if (!TransformUtil.isStatic(vb)) {
			return;
		}

		ctx.softDep(vb.getType());

		Object cv = TransformUtil.constantValue(vb);
		print(TransformUtil
				.fieldModifiers(vb.getModifiers(), false, cv != null));
		print(TransformUtil.qualifiedCName(vb.getType()));
		print(" ");

		print(TransformUtil.ref(vb.getType()));
		print(TransformUtil.qualifiedCName(vb.getDeclaringClass()));
		print("::");
		print(vb.getName());
		println("_;");
	}

	private void printMethod(ITypeBinding tb, IMethodBinding mb)
			throws Exception {
		if (Modifier.isAbstract(mb.getModifiers())) {
			return;
		}

		if (Modifier.isPrivate(mb.getModifiers())) {
			println("/* private: xxx " + mb.getName() + "(...) */");
			return;
		}

		if (!mb.isConstructor()) {
			ITypeBinding rt = mb.getReturnType();
			ctx.softDep(rt);

			print(TransformUtil.qualifiedCName(rt));
			print(" ");
			print(TransformUtil.ref(rt));
		} else {
			print("void ");
		}

		print(TransformUtil.qualifiedCName(tb));
		print("::");

		print(mb.isConstructor() ? "_construct" : TransformUtil.keywords(mb
				.getMethodDeclaration().getName()));

		TransformUtil.printParams(pw, tb, mb, ctx);
		pw.println();
		print("{");
		if (Modifier.isNative(mb.getModifiers())) {
			print(" /* native */");
		} else {
			print(" /* stub */");
		}

		pw.println();
		boolean hasBody = false;
		for (Snippet snippet : ctx.snippets) {
			if (!snippet.body(ctx, this, mb)) {
				hasBody = true;
				break;
			}
		}

		if (!hasBody) {
			if (mb.getReturnType() != null
					&& !mb.getReturnType().getName().equals("void")) {
				print(TransformUtil.indent(1));
				println("return 0;");
			}
		}

		println("}");
		pw.println();

		for (ITypeBinding dep : TransformUtil.defineBridge(pw, type, mb, ctx)) {
			hardDep(dep);
		}
	}

	public void print(String string) {
		pw.print(string);
	}

	public void println(String string) {
		pw.println(string);
	}
}
