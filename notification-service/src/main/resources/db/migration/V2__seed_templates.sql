-- Placeholders look like {{name}}. Unknown placeholders render as empty text.
INSERT INTO templates (code, channel, subject_template, body_template) VALUES

('WELCOME', 'EMAIL',
 'Welcome to GadgetMart, {{name}}!',
 'Hi {{name}},

Your GadgetMart account is ready. Browse certified second-hand phones, laptops, tablets and more, each with a clear condition grade and warranty.

Happy shopping,
Team GadgetMart'),

('WELCOME', 'IN_APP',
 'Welcome to GadgetMart!',
 'Your account is ready. Browse certified second-hand gadgets with a clear condition grade and warranty.'),

('ORDER_PAID', 'EMAIL',
 'Order {{orderNumber}} confirmed',
 'Hi {{name}},

Thank you for your order {{orderNumber}}.

Items: {{items}}
Total: {{total}}
Delivering to: {{city}}

We will email you again when it ships.

Team GadgetMart'),

('ORDER_PAID', 'SMS',
 'GadgetMart',
 'GadgetMart: order {{orderNumber}} confirmed. Total {{total}}. Thank you!'),

('ORDER_PAID', 'IN_APP',
 'Order {{orderNumber}} confirmed',
 '{{items}}. Total {{total}}.'),

('ORDER_CANCELLED', 'EMAIL',
 'Order {{orderNumber}} was cancelled',
 'Hi {{name}},

Your order {{orderNumber}} ({{total}}) could not be completed and was cancelled.
Reason: {{reason}}

You have not been charged, or any payment taken has been refunded. You can place the order again at any time.

Team GadgetMart'),

('ORDER_CANCELLED', 'IN_APP',
 'Order {{orderNumber}} was cancelled',
 '{{reason}}. You have not been charged, or any payment has been refunded.');
