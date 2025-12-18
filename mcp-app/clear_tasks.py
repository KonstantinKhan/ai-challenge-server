#!/usr/bin/env python3
"""
Скрипт для очистки задач из базы данных MCP сервера
"""
import sqlite3
import os
import sys

DB_PATH = "tasks.db"

def clear_all_tasks(force=False):
    """Удаляет все задачи из базы данных"""
    if not os.path.exists(DB_PATH):
        print(f"❌ База данных не найдена: {DB_PATH}")
        return

    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()

    try:
        # Подсчитываем задачи до удаления
        cursor.execute("SELECT COUNT(*) FROM tasks")
        count_before = cursor.fetchone()[0]
        print(f"📊 Задач в базе: {count_before}")

        if count_before == 0:
            print("✅ База уже пуста!")
            return

        # Показываем задачи перед удалением
        cursor.execute("SELECT id, title, status FROM tasks")
        tasks = cursor.fetchall()
        print("\n📋 Задачи в базе:")
        for task_id, title, status in tasks:
            print(f"  [{task_id}] {title} ({status})")

        # Подтверждение
        if not force:
            confirm = input(f"\n⚠️  Удалить все {count_before} задач(и)? (yes/y): ").strip().lower()
            if confirm not in ['yes', 'y']:
                print("❌ Отменено")
                return

        # Удаляем все задачи
        cursor.execute("DELETE FROM tasks")
        conn.commit()

        # Проверяем
        cursor.execute("SELECT COUNT(*) FROM tasks")
        count_after = cursor.fetchone()[0]

        print(f"\n✅ Удалено задач: {count_before}")
        print(f"✅ Осталось задач: {count_after}")

    except Exception as e:
        print(f"❌ Ошибка: {e}")
        conn.rollback()
    finally:
        conn.close()

if __name__ == "__main__":
    force = "--force" in sys.argv or "-f" in sys.argv
    clear_all_tasks(force=force)
