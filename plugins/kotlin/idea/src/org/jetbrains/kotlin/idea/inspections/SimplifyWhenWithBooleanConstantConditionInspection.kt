// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.*
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.codeinsight.utils.isFalseConstant
import org.jetbrains.kotlin.idea.codeinsight.utils.isTrueConstant
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.resolve.bindingContextUtil.isUsedAsExpression

import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.util.replaceWithBranchAndMoveCaret

class SimplifyWhenWithBooleanConstantConditionInspection : AbstractKotlinInspection() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
        return whenExpressionVisitor(fun(expression) {
            if (expression.closeBrace == null) return
            if (expression.subjectExpression != null) return
            if (expression.entries.none { it.isTrueConstantCondition() || it.isFalseConstantCondition() }) return

            holder.registerProblem(
                expression.whenKeyword,
                KotlinBundle.message("this.when.is.simplifiable"),
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                SimplifyWhenFix()
            )
        })
    }
}

private class SimplifyWhenFix : LocalQuickFix {
    override fun getName() = KotlinBundle.message("simplify.when.fix.text")

    override fun getFamilyName() = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement.getStrictParentOfType<KtWhenExpression>() ?: return
        val closeBrace = element.closeBrace ?: return
        val factory = KtPsiFactory(project)
        val usedAsExpression = element.isUsedAsExpression(element.analyze())
        element.deleteFalseEntries(usedAsExpression)
        element.replaceTrueEntry(usedAsExpression, closeBrace, factory)
    }
}

private fun KtWhenExpression.deleteFalseEntries(usedAsExpression: Boolean) {
    for (entry in entries) {
        if (entry.isFalseConstantCondition()) {
            entry.delete()
        }
    }

    val entries = entries
    if (entries.isEmpty() && !usedAsExpression) {
        delete()
    } else if (entries.singleOrNull()?.isElse == true) {
        elseExpression?.let { replaceWithBranchAndMoveCaret(it, usedAsExpression) }
    }
}

private fun KtWhenExpression.replaceTrueEntry(usedAsExpression: Boolean, closeBrace: PsiElement, factory: KtPsiFactory) {
    val entries = entries
    val trueIndex = entries.indexOfFirst { it.isTrueConstantCondition() }
    if (trueIndex == -1) return

    val expression = entries[trueIndex].expression ?: return

    if (trueIndex == 0) {
        replaceWithBranchAndMoveCaret(expression, usedAsExpression)
    } else {
        val elseEntry = factory.createWhenEntry("else -> ${expression.text}")
        for (entry in entries.subList(trueIndex, entries.size)) {
            entry.delete()
        }
        addBefore(elseEntry, closeBrace)
    }
}

private fun KtWhenEntry.isTrueConstantCondition(): Boolean =
    (conditions.singleOrNull() as? KtWhenConditionWithExpression)?.expression.isTrueConstant()

private fun KtWhenEntry.isFalseConstantCondition(): Boolean =
    (conditions.singleOrNull() as? KtWhenConditionWithExpression)?.expression.isFalseConstant()