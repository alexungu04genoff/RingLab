-- Isolated Sonic Wiki artwork for the stock machines added in V10.
UPDATE machines SET image_path = '/assets/machines/' || artwork.slug || '.webp'
FROM (VALUES
    ('Ancient Throne', 'ancient-throne'), ('Beast Spike', 'beast-spike'),
    ('Beat Gator', 'beat-gator'), ('Buster M', 'buster-m'),
    ('Cross Dozer', 'cross-dozer'), ('Cyber Spear', 'cyber-spear'),
    ('Egg Drillster Mk.II', 'egg-drillster-mk-ii'), ('Fang Loader', 'fang-loader'),
    ('Fossil Rock', 'fossil-rock'), ('Frog Cruiser', 'frog-cruiser'),
    ('Giganto Liner', 'giganto-liner'), ('Hot Hatch', 'hot-hatch'),
    ('Hyper Scorpion', 'hyper-scorpion'), ('Jaws Rocket', 'jaws-rocket'),
    ('Knight Tank', 'knight-tank'), ('Lip Spyder', 'lip-spyder'),
    ('Little Lady', 'little-lady'), ('Long Stinger', 'long-stinger'),
    ('Mirage Blade', 'mirage-blade'), ('Moto Beetle', 'moto-beetle'),
    ('Pawn Calibur', 'pawn-calibur'), ('Pop Float', 'pop-float'),
    ('Quad Copter', 'quad-copter'), ('Racing Buggy', 'racing-buggy'),
    ('Radical Fours', 'radical-fours'), ('Retro Future', 'retro-future'),
    ('Royal Chariot', 'royal-chariot'), ('Sakura Board', 'sakura-board'),
    ('Stealth Chaser', 'stealth-chaser'), ('Super Shining', 'super-shining'),
    ('Trail Runner', 'trail-runner'), ('Triple Fan', 'triple-fan'),
    ('TYPE-W Windy', 'type-w-windy'), ('Victoria Carriage', 'victoria-carriage'),
    ('Wild GT', 'wild-gt'), ('Wispon Booster', 'wispon-booster')
) AS artwork(name, slug)
WHERE machines.name = artwork.name;
