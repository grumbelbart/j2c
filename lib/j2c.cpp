#include <stdint.h>

#include <java.lang.Class.h>
#include <java.lang.ClassLoader.h>

#include <Array.h>
#include <java.lang.String.h>

using namespace java::lang;

void lock(Object *) { }

void unlock(Object *) { }

String *java::lang::operator "" _j(const char16_t * p, size_t n)
{
    char16_tArray *x = new char16_tArray(p, n);
	String *s = new String();
	s->value_ = x;
	s->count_ = n;
    return s->intern(); 
}

Class *class_(const char16_t *s, int n)
{
    return Class::forName(operator "" _j(s, n), false, ClassLoader::getCallerClassLoader());
}

