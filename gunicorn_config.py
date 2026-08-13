import multiprocessing
import os

bind = f"0.0.0.0:{os.environ.get('PORT', '5000')}"
workers = min(4, max(2, multiprocessing.cpu_count()))
threads = 2
timeout = 120
accesslog = "-"
errorlog = "-"
loglevel = "info"
